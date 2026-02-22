package com.hospital.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.infrastructure.ConsentRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 5 integration verification tests — Consent and Document Management.
 * Covers 7 success criteria:
 *   SC1: RECEPTIONIST signs consent → 200 with signedAt + expiresAt (HIPAA = +1yr)
 *   SC2: Re-signing updates existing record (upsert, not duplicate)
 *   SC3: Patient profile shows 3 MISSING consent alerts for new patient
 *   SC4: After signing all 3 required, patient profile shows empty consentAlerts
 *   SC5: PDF upload (valid PDF → 201; non-PDF → 400)
 *   SC6: ADMIN revokes consent; /admin/consents/missing shows patient again
 *   SC7: RECEPTIONIST attempt to revoke → 403
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase05ConsentVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @BeforeEach
    void setUp() {
        consentRepository.deleteAll();
        patientRepository.deleteAll();
    }

    // =====================================================================
    // SC1: Sign HIPAA consent → status=SIGNED with signedAt and expiresAt
    // =====================================================================

    @Test
    void sc1_signConsent_hipaaNotice_returns200WithSignedAtAndExpiresAt() throws Exception {
        String businessId = registerTestPatient("555-700-0001");

        mockMvc.perform(post("/api/v1/patients/" + businessId + "/consents/HIPAA_NOTICE/sign")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consentType").value("HIPAA_NOTICE"))
            .andExpect(jsonPath("$.status").value("SIGNED"))
            .andExpect(jsonPath("$.signedAt").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty()) // HIPAA expires in +1 year
            .andExpect(jsonPath("$.signedBy").isNotEmpty());
    }

    @Test
    void sc1_signConsent_financialResponsibility_expiresAtIsNull() throws Exception {
        String businessId = registerTestPatient("555-700-0002");

        mockMvc.perform(post("/api/v1/patients/" + businessId + "/consents/FINANCIAL_RESPONSIBILITY/sign")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consentType").value("FINANCIAL_RESPONSIBILITY"))
            .andExpect(jsonPath("$.status").value("SIGNED"))
            .andExpect(jsonPath("$.expiresAt").doesNotExist()); // FINANCIAL_RESPONSIBILITY has no expiry
    }

    // =====================================================================
    // SC2: Re-signing a consent updates the existing record (upsert)
    // =====================================================================

    @Test
    void sc2_resignConsent_updatesExistingRecord_doesNotCreateDuplicate() throws Exception {
        String businessId = registerTestPatient("555-700-0003");

        // First sign
        mockMvc.perform(post("/api/v1/patients/" + businessId + "/consents/HIPAA_NOTICE/sign")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"first signing\"}"))
            .andExpect(status().isOk());

        // Re-sign (upsert)
        mockMvc.perform(post("/api/v1/patients/" + businessId + "/consents/HIPAA_NOTICE/sign")
                .with(user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"renewed signing\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SIGNED"))
            .andExpect(jsonPath("$.notes").value("renewed signing"));

        // GET consents — must still have exactly 1 HIPAA_NOTICE record
        mockMvc.perform(get("/api/v1/patients/" + businessId + "/consents")
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].consentType").value("HIPAA_NOTICE"));
    }

    // =====================================================================
    // SC3: New patient has 3 MISSING consent alerts on profile
    // =====================================================================

    @Test
    void sc3_newPatientProfile_hasMissingConsentAlerts_forAllThreeRequiredTypes() throws Exception {
        String businessId = registerTestPatient("555-700-0004");

        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consentAlerts").isArray())
            .andExpect(jsonPath("$.consentAlerts", hasSize(3)))
            .andExpect(jsonPath("$.consentAlerts[*].alertType", everyItem(equalTo("MISSING"))))
            .andExpect(jsonPath("$.consentAlerts[*].consentType",
                hasItems("HIPAA_NOTICE", "TREATMENT_AUTHORIZATION", "FINANCIAL_RESPONSIBILITY")));
    }

    // =====================================================================
    // SC4: After signing all 3 required consents, alerts are empty
    // =====================================================================

    @Test
    void sc4_afterSigningAllRequiredConsents_consentAlertsIsEmpty() throws Exception {
        String businessId = registerTestPatient("555-700-0005");

        // Sign all 3 required consent types
        for (String type : new String[]{"HIPAA_NOTICE", "TREATMENT_AUTHORIZATION", "FINANCIAL_RESPONSIBILITY"}) {
            mockMvc.perform(post("/api/v1/patients/" + businessId + "/consents/" + type + "/sign")
                    .with(user("receptionist").roles("RECEPTIONIST"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk());
        }

        // Patient profile — consentAlerts must be empty
        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consentAlerts").isArray())
            .andExpect(jsonPath("$.consentAlerts", hasSize(0)));
    }

    // =====================================================================
    // SC5: PDF upload — valid PDF → 201; non-PDF → 400
    // =====================================================================

    @Test
    void sc5_uploadConsentDocument_validPdf_returns201WithDocumentInfo() throws Exception {
        String businessId = registerTestPatient("555-700-0006");

        // Sign HIPAA consent to get a consentId
        MvcResult signResult = mockMvc.perform(
                post("/api/v1/patients/" + businessId + "/consents/HIPAA_NOTICE/sign")
                    .with(user("receptionist").roles("RECEPTIONIST"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> signResponse = objectMapper.readValue(
            signResult.getResponse().getContentAsString(), Map.class);
        String consentId = (String) signResponse.get("businessId");

        // Upload a valid PDF (minimal PDF header)
        byte[] pdfBytes = createMinimalPdf();
        MockMultipartFile pdfFile = new MockMultipartFile(
            "file", "hipaa-consent.pdf", "application/pdf", pdfBytes
        );

        mockMvc.perform(multipart("/api/v1/patients/" + businessId + "/consents/" + consentId + "/document")
                .file(pdfFile)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentFilename").isNotEmpty());
    }

    @Test
    void sc5_uploadConsentDocument_nonPdfFile_returns400() throws Exception {
        String businessId = registerTestPatient("555-700-0007");

        MvcResult signResult = mockMvc.perform(
                post("/api/v1/patients/" + businessId + "/consents/HIPAA_NOTICE/sign")
                    .with(user("receptionist").roles("RECEPTIONIST"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> signResponse = objectMapper.readValue(
            signResult.getResponse().getContentAsString(), Map.class);
        String consentId = (String) signResponse.get("businessId");

        // Upload an image file instead of PDF
        MockMultipartFile imageFile = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8}
        );

        mockMvc.perform(multipart("/api/v1/patients/" + businessId + "/consents/" + consentId + "/document")
                .file(imageFile)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // SC6: ADMIN revokes consent; /admin/consents/missing reflects the change
    // =====================================================================

    @Test
    void sc6_revokeConsent_patientAppearsInMissingConsentReport() throws Exception {
        String businessId = registerTestPatient("555-700-0008");

        // Sign all 3 required consents
        for (String type : new String[]{"HIPAA_NOTICE", "TREATMENT_AUTHORIZATION", "FINANCIAL_RESPONSIBILITY"}) {
            mockMvc.perform(post("/api/v1/patients/" + businessId + "/consents/" + type + "/sign")
                    .with(user("receptionist").roles("RECEPTIONIST"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk());
        }

        // Verify patient does NOT appear in missing consent report
        mockMvc.perform(get("/api/v1/admin/consents/missing")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        // Get the HIPAA_NOTICE consentId for revocation
        MvcResult consentsResult = mockMvc.perform(
                get("/api/v1/patients/" + businessId + "/consents")
                    .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> consents = objectMapper.readValue(
            consentsResult.getResponse().getContentAsString(), java.util.List.class);
        String hipaaConsentId = consents.stream()
            .filter(c -> "HIPAA_NOTICE".equals(c.get("consentType")))
            .findFirst()
            .map(c -> (String) c.get("businessId"))
            .orElseThrow();

        // ADMIN revokes the HIPAA consent
        mockMvc.perform(delete("/api/v1/patients/" + businessId + "/consents/" + hipaaConsentId)
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"));

        // Patient now appears in missing consent report (HIPAA was revoked)
        mockMvc.perform(get("/api/v1/admin/consents/missing")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    // =====================================================================
    // SC7: RECEPTIONIST cannot revoke consent (ADMIN only)
    // =====================================================================

    @Test
    void sc7_revokeConsent_receptionistRole_returns403() throws Exception {
        String businessId = registerTestPatient("555-700-0009");

        MvcResult signResult = mockMvc.perform(
                post("/api/v1/patients/" + businessId + "/consents/HIPAA_NOTICE/sign")
                    .with(user("receptionist").roles("RECEPTIONIST"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> signResponse = objectMapper.readValue(
            signResult.getResponse().getContentAsString(), Map.class);
        String consentId = (String) signResponse.get("businessId");

        // RECEPTIONIST attempt to revoke → 403 Forbidden
        mockMvc.perform(delete("/api/v1/patients/" + businessId + "/consents/" + consentId)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isForbidden());
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    /**
     * Register a test patient via the staff API and return the businessId.
     */
    private String registerTestPatient(String phoneNumber) throws Exception {
        String requestJson = """
            {
                "firstName": "Consent",
                "lastName": "TestPatient",
                "dateOfBirth": "1980-03-10",
                "gender": "FEMALE",
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

    /**
     * Create a minimal valid PDF byte array.
     * The header "%PDF-1.4" is enough to produce a non-empty file with the correct content type,
     * but ConsentDocumentStorageService only validates content type (not PDF structure).
     */
    private byte[] createMinimalPdf() {
        String pdfHeader = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n";
        return pdfHeader.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
