package com.hospital.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.billing.infrastructure.InvoiceLineItemRepository;
import com.hospital.billing.infrastructure.InvoiceRepository;
import com.hospital.billing.infrastructure.PaymentRepository;
import com.hospital.patient.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 6 integration verification tests — Clinical EMR features.
 * Covers 5 success criteria:
 *   SC1: Vital Signs — NURSE records; each recording appends (not updates)
 *   SC2: Clinical Notes — SOAP lifecycle; RECEPTIONIST blocked; finalize locks
 *   SC3: Prescriptions — refill decrement; 0 refills → 400; RECEPTIONIST blocked
 *   SC4: Lab Results — order lifecycle; abnormal flag surfaced in /abnormal endpoint
 *   SC5: Billing — invoice + line items; issue; partial payment → PARTIALLY_PAID; full → PAID; extra → 400
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase06ClinicalVerificationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PatientRepository patientRepository;
    @Autowired private VitalSignsRepository vitalSignsRepository;
    @Autowired private ClinicalNoteRepository clinicalNoteRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceLineItemRepository lineItemRepository;
    @Autowired private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        lineItemRepository.deleteAll();
        invoiceRepository.deleteAll();
        labResultRepository.deleteAll();
        labOrderRepository.deleteAll();
        prescriptionRepository.deleteAll();
        clinicalNoteRepository.deleteAll();
        vitalSignsRepository.deleteAll();
        patientRepository.deleteAll();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String createPatient() throws Exception {
        String req = """
            {
                "firstName": "Test",
                "lastName": "Patient",
                "dateOfBirth": "1990-01-01",
                "gender": "MALE",
                "phoneNumber": "555-900-0001",
                "photoIdVerified": true
            }
            """;
        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("businessId").asText();
    }

    private String extractField(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asText();
    }

    // =========================================================================
    // SC1: Vital Signs
    // =========================================================================

    @Test
    void sc1_nurseRecordsVitals_returns201WithBusinessId() throws Exception {
        String patientId = createPatient();

        mockMvc.perform(post("/api/v1/patients/{id}/vitals", patientId)
                .with(user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "temperature": 37.0,
                        "systolicBp": 120,
                        "diastolicBp": 80,
                        "heartRate": 72
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.businessId").isNotEmpty())
            .andExpect(jsonPath("$.temperature").value(37.0))
            .andExpect(jsonPath("$.systolicBp").value(120));
    }

    @Test
    void sc1_secondVitalsRecordingAppendsNotUpdates_getReturnsSize2() throws Exception {
        String patientId = createPatient();

        String vitals = """
            {"temperature": 37.0, "heartRate": 70}
            """;
        mockMvc.perform(post("/api/v1/patients/{id}/vitals", patientId)
                .with(user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON).content(vitals))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/patients/{id}/vitals", patientId)
                .with(user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"temperature": 38.2, "heartRate": 95}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/patients/{id}/vitals", patientId)
                .with(user("nurse").roles("NURSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    // =========================================================================
    // SC2: Clinical Notes
    // =========================================================================

    @Test
    void sc2_doctorCreatesSoapNote_returns201() throws Exception {
        String patientId = createPatient();

        mockMvc.perform(post("/api/v1/patients/{id}/notes", patientId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "noteType": "SOAP",
                        "subjective": "Patient complains of headache",
                        "objective": "BP 140/90, HR 88",
                        "assessment": "Hypertension, uncontrolled",
                        "plan": "Start amlodipine 5mg daily"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.businessId").isNotEmpty())
            .andExpect(jsonPath("$.finalized").value(false));
    }

    @Test
    void sc2_receptionistCreatesNote_returns403() throws Exception {
        String patientId = createPatient();

        mockMvc.perform(post("/api/v1/patients/{id}/notes", patientId)
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteType\": \"SOAP\", \"subjective\": \"test\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void sc2_doctorUpdatesBeforeFinalize_returns200() throws Exception {
        String patientId = createPatient();

        MvcResult createResult = mockMvc.perform(post("/api/v1/patients/{id}/notes", patientId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteType\": \"SOAP\", \"assessment\": \"Initial assessment\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String noteId = extractField(createResult, "businessId");

        mockMvc.perform(put("/api/v1/patients/{pid}/notes/{nid}", patientId, noteId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assessment\": \"Updated assessment after review\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assessment").value("Updated assessment after review"));
    }

    @Test
    void sc2_finalizeNote_locksItAndUpdateAfterFinalizeReturns409() throws Exception {
        String patientId = createPatient();

        MvcResult createResult = mockMvc.perform(post("/api/v1/patients/{id}/notes", patientId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteType\": \"SOAP\", \"assessment\": \"Final diagnosis\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String noteId = extractField(createResult, "businessId");

        mockMvc.perform(post("/api/v1/patients/{pid}/notes/{nid}/finalize", patientId, noteId)
                .with(user("doctor").roles("DOCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finalized").value(true));

        // Update after finalize should fail with 409
        mockMvc.perform(put("/api/v1/patients/{pid}/notes/{nid}", patientId, noteId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assessment\": \"Attempted edit after finalize\"}"))
            .andExpect(status().isConflict());
    }

    // =========================================================================
    // SC3: Prescriptions
    // =========================================================================

    @Test
    void sc3_doctorPrescribesWithRefills_refillDecrementsCorrectly() throws Exception {
        String patientId = createPatient();

        MvcResult prescribeResult = mockMvc.perform(post("/api/v1/patients/{id}/prescriptions", patientId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "medicationName": "Metformin",
                        "dosage": "500mg",
                        "frequency": "twice daily",
                        "refillsRemaining": 2
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.refillsRemaining").value(2))
            .andReturn();

        String prescriptionId = extractField(prescribeResult, "businessId");

        // First refill: 2 → 1
        mockMvc.perform(post("/api/v1/patients/{pid}/prescriptions/{rid}/refill", patientId, prescriptionId)
                .with(user("nurse").roles("NURSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refillsRemaining").value(1));

        // Second refill: 1 → 0
        mockMvc.perform(post("/api/v1/patients/{pid}/prescriptions/{rid}/refill", patientId, prescriptionId)
                .with(user("nurse").roles("NURSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refillsRemaining").value(0));

        // Third refill at 0 → 400
        mockMvc.perform(post("/api/v1/patients/{pid}/prescriptions/{rid}/refill", patientId, prescriptionId)
                .with(user("nurse").roles("NURSE")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sc3_receptionistPrescribes_returns403() throws Exception {
        String patientId = createPatient();

        mockMvc.perform(post("/api/v1/patients/{id}/prescriptions", patientId)
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "medicationName": "Aspirin",
                        "dosage": "100mg",
                        "frequency": "once daily"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SC4: Lab Results
    // =========================================================================

    @Test
    void sc4_doctorOrdersLab_nurseUpdatesStatus_addsAbnormalResult_appearsInAbnormalEndpoint() throws Exception {
        String patientId = createPatient();

        // Doctor creates lab order
        MvcResult orderResult = mockMvc.perform(post("/api/v1/patients/{id}/lab-orders", patientId)
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "orderName": "Complete Blood Count",
                        "priority": "ROUTINE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();

        String orderId = extractField(orderResult, "businessId");

        // Nurse updates status to COLLECTED
        mockMvc.perform(patch("/api/v1/lab-orders/{id}/status", orderId)
                .with(user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"COLLECTED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COLLECTED"));

        // Nurse updates status to IN_LAB
        mockMvc.perform(patch("/api/v1/lab-orders/{id}/status", orderId)
                .with(user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"IN_LAB\"}"))
            .andExpect(status().isOk());

        // Nurse adds abnormal result
        mockMvc.perform(post("/api/v1/lab-orders/{id}/results", orderId)
                .with(user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "testName": "WBC",
                        "resultValue": "14.5",
                        "unit": "10^3/µL",
                        "referenceRange": "4.5-11.0",
                        "abnormal": true,
                        "abnormalFlag": "H"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.abnormal").value(true));

        // Abnormal results endpoint should return the result
        mockMvc.perform(get("/api/v1/patients/{id}/lab-orders/abnormal", patientId)
                .with(user("doctor").roles("DOCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].abnormalFlag").value("H"));
    }

    // =========================================================================
    // SC5: Billing
    // =========================================================================

    @Test
    void sc5_invoiceLifecycle_createIssuePayPartiallyThenFullyPaid() throws Exception {
        String patientId = createPatient();

        // Create invoice with 2 line items: 50 + 75 = 125
        MvcResult invoiceResult = mockMvc.perform(post("/api/v1/patients/{id}/invoices", patientId)
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "lineItems": [
                            {"description": "Consultation", "quantity": 1, "unitPrice": 50.00},
                            {"description": "Lab Fee", "quantity": 1, "unitPrice": 75.00}
                        ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(125.00))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.lineItems", hasSize(2)))
            .andReturn();

        String invoiceId = extractField(invoiceResult, "businessId");

        // Issue invoice
        mockMvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId)
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ISSUED"));

        // Record partial payment: 50
        mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "amount": 50.00,
                        "paymentMethod": "CASH",
                        "paymentDate": "2026-02-01"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
            .andExpect(jsonPath("$.paidAmount").value(50.00));

        // Record remaining payment: 75
        mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "amount": 75.00,
                        "paymentMethod": "CREDIT_CARD",
                        "paymentDate": "2026-02-02"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.paidAmount").value(125.00));

        // Extra payment on PAID invoice → 409
        mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "amount": 10.00,
                        "paymentMethod": "CASH",
                        "paymentDate": "2026-02-03"
                    }
                    """))
            .andExpect(status().isConflict());
    }
}
