package com.hospital.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.infrastructure.AppointmentRepository;
import com.hospital.patient.infrastructure.DoctorAvailabilityRepository;
import com.hospital.patient.infrastructure.PatientRepository;
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

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 5 integration verification tests — Appointment scheduling.
 * Covers 6 success criteria:
 *   SC1: Doctor availability setup (ADMIN sets, DOCTOR role rejected)
 *   SC2: Book appointment (valid slot + duplicate slot rejected)
 *   SC3: Status transitions (SCHEDULED→CONFIRMED→IN_PROGRESS→COMPLETED, terminal blocked)
 *   SC4: Patient profile includes upcomingAppointments field
 *   SC5: Available slots excludes booked, includes free
 *   SC6: Patient goes INACTIVE → future appointments auto-cancelled
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase05AppointmentVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    private static final String DOCTOR_ID = "DR-APPT-TEST-001";
    // Must be strictly in the future to pass @Future validation on BookAppointmentRequest
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        availabilityRepository.deleteAll();
        patientRepository.deleteAll();
    }

    // =====================================================================
    // SC1: Doctor availability — ADMIN sets, DOCTOR role cannot
    // =====================================================================

    @Test
    void sc1_setDoctorAvailability_adminRole_returns201WithAvailabilityDetails() throws Exception {
        String requestJson = """
            {
                "dayOfWeek": "MONDAY",
                "startTime": "09:00",
                "endTime": "17:00",
                "slotDurationMinutes": 30
            }
            """;

        mockMvc.perform(post("/api/v1/doctors/" + DOCTOR_ID + "/availability")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.doctorId").value(DOCTOR_ID))
            .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
            .andExpect(jsonPath("$.startTime").isNotEmpty())
            .andExpect(jsonPath("$.endTime").isNotEmpty())
            .andExpect(jsonPath("$.slotDurationMinutes").value(30));
    }

    @Test
    void sc1_setDoctorAvailability_doctorRole_returns403() throws Exception {
        String requestJson = """
            {
                "dayOfWeek": "TUESDAY",
                "startTime": "09:00",
                "endTime": "17:00"
            }
            """;

        mockMvc.perform(post("/api/v1/doctors/" + DOCTOR_ID + "/availability")
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isForbidden());
    }

    // =====================================================================
    // SC2: Book appointment — valid slot → 201, duplicate slot → error
    // =====================================================================

    @Test
    void sc2_bookAppointment_validSlot_returns201WithScheduledStatus() throws Exception {
        String businessId = registerTestPatient("555-900-0001");

        String bookingJson = """
            {
                "patientBusinessId": "%s",
                "doctorId": "%s",
                "appointmentDate": "%s",
                "startTime": "10:00",
                "type": "CONSULTATION"
            }
            """.formatted(businessId, DOCTOR_ID, FUTURE_DATE);

        mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.businessId").isNotEmpty())
            .andExpect(jsonPath("$.doctorId").value(DOCTOR_ID))
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.type").value("CONSULTATION"))
            .andExpect(jsonPath("$.patientBusinessId").value(businessId));
    }

    @Test
    void sc2_bookAppointment_duplicateSlot_returns400() throws Exception {
        String businessId = registerTestPatient("555-900-0002");

        String bookingJson = """
            {
                "patientBusinessId": "%s",
                "doctorId": "%s",
                "appointmentDate": "%s",
                "startTime": "11:00",
                "type": "FOLLOW_UP"
            }
            """.formatted(businessId, DOCTOR_ID, FUTURE_DATE);

        // First booking succeeds
        mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isCreated());

        // Second booking for the same slot must fail (IllegalArgumentException → 400)
        mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // SC3: Status transitions — SCHEDULED→CONFIRMED→IN_PROGRESS→COMPLETED
    //       then COMPLETED→CANCELLED must be rejected (terminal state → 409)
    // =====================================================================

    @Test
    void sc3_appointmentStatusTransitions_fullLifecycleAndTerminalBlocked() throws Exception {
        String businessId = registerTestPatient("555-900-0003");

        String bookingJson = """
            {
                "patientBusinessId": "%s",
                "doctorId": "%s",
                "appointmentDate": "%s",
                "startTime": "14:00",
                "type": "CONSULTATION"
            }
            """.formatted(businessId, DOCTOR_ID, FUTURE_DATE);

        MvcResult bookResult = mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> appt = objectMapper.readValue(
            bookResult.getResponse().getContentAsString(), Map.class);
        String appointmentId = (String) appt.get("businessId");

        // SCHEDULED → CONFIRMED
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/status")
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONFIRMED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // CONFIRMED → IN_PROGRESS
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/status")
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // IN_PROGRESS → COMPLETED
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/status")
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // COMPLETED → CANCELLED must be rejected (terminal state, IllegalStateException → 409 Conflict)
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/status")
                .with(user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\",\"cancelReason\":\"late change\"}"))
            .andExpect(status().isConflict());
    }

    // =====================================================================
    // SC4: Patient profile includes upcomingAppointments array
    // =====================================================================

    @Test
    void sc4_getPatientProfile_includesUpcomingAppointmentsField() throws Exception {
        String businessId = registerTestPatient("555-900-0004");

        // Book a future appointment
        String bookingJson = """
            {
                "patientBusinessId": "%s",
                "doctorId": "%s",
                "appointmentDate": "%s",
                "startTime": "15:00",
                "type": "CONSULTATION"
            }
            """.formatted(businessId, DOCTOR_ID, FUTURE_DATE);

        mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isCreated());

        // GET patient profile — must include upcoming appointment
        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upcomingAppointments").isArray())
            .andExpect(jsonPath("$.upcomingAppointments", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.upcomingAppointments[0].doctorId").value(DOCTOR_ID))
            .andExpect(jsonPath("$.upcomingAppointments[0].status").value("SCHEDULED"));
    }

    // =====================================================================
    // SC5: Available slots excludes booked, includes free slots
    // =====================================================================

    @Test
    void sc5_getAvailableSlots_excludesBookedSlot_includesFreeSlots() throws Exception {
        // Set availability for the exact day of week of FUTURE_DATE (2h window, 30-min slots = 4 total)
        String dayOfWeek = FUTURE_DATE.getDayOfWeek().name();
        String availJson = """
            {
                "dayOfWeek": "%s",
                "startTime": "09:00",
                "endTime": "11:00",
                "slotDurationMinutes": 30
            }
            """.formatted(dayOfWeek);

        mockMvc.perform(post("/api/v1/doctors/" + DOCTOR_ID + "/availability")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(availJson))
            .andExpect(status().isCreated());

        // Book the 09:00 slot
        String businessId = registerTestPatient("555-900-0005");
        String bookingJson = """
            {
                "patientBusinessId": "%s",
                "doctorId": "%s",
                "appointmentDate": "%s",
                "startTime": "09:00",
                "type": "CONSULTATION"
            }
            """.formatted(businessId, DOCTOR_ID, FUTURE_DATE);

        mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isCreated());

        // GET available slots: 4 total - 1 booked (09:00) = 3 remaining
        mockMvc.perform(get("/api/v1/doctors/" + DOCTOR_ID + "/available-slots")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .param("date", FUTURE_DATE.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.doctorId").value(DOCTOR_ID))
            .andExpect(jsonPath("$.availableSlots").isArray())
            .andExpect(jsonPath("$.availableSlots", hasSize(3)));
    }

    // =====================================================================
    // SC6: Patient goes INACTIVE → future appointments auto-cancelled
    // =====================================================================

    @Test
    void sc6_patientBecomesInactive_futureAppointmentsAutoCancelled() throws Exception {
        String businessId = registerTestPatient("555-900-0006");

        // Book a future appointment
        String bookingJson = """
            {
                "patientBusinessId": "%s",
                "doctorId": "%s",
                "appointmentDate": "%s",
                "startTime": "16:00",
                "type": "CONSULTATION"
            }
            """.formatted(businessId, DOCTOR_ID, FUTURE_DATE);

        MvcResult bookResult = mockMvc.perform(post("/api/v1/appointments")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> appt = objectMapper.readValue(
            bookResult.getResponse().getContentAsString(), Map.class);
        String appointmentId = (String) appt.get("businessId");

        // Change patient status to INACTIVE (ADMIN only)
        mockMvc.perform(patch("/api/v1/patients/" + businessId + "/status")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Wait for the @Async PatientUpdatedEventListener to fire AFTER_COMMIT
        // and cancel future appointments via AppointmentService.cancelFutureAppointmentsForInactivePatient
        Thread.sleep(1000);

        // Appointment must now be CANCELLED
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    /**
     * Register a test patient via the API and return the businessId.
     * Uses per-request RECEPTIONIST auth (NOT @WithMockUser — must not be used on private methods).
     */
    private String registerTestPatient(String phoneNumber) throws Exception {
        String requestJson = """
            {
                "firstName": "Appt",
                "lastName": "TestPatient",
                "dateOfBirth": "1985-06-15",
                "gender": "MALE",
                "phoneNumber": "%s",
                "photoIdVerified": true
            }
            """.formatted(phoneNumber);

        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("businessId");
    }
}
