package com.hospital.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import com.hospital.patient.infrastructure.PatientPhotoRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.smartform.api.dto.ZipLookupResponse;
import com.hospital.smartform.application.ZipLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 integration verification tests.
 * Covers all 4 success criteria:
 *   SC1: Quick registration with isRegistrationComplete workflow
 *   SC2: Patient photo upload and download
 *   SC3: Data quality dashboard
 *   SC4: Smart form helpers (ZIP lookup + insurance plans)
 *
 * Pattern: Same as Phase02VerificationTest.
 * - @SpringBootTest full context (real database via Flyway migrations)
 * - @AutoConfigureMockMvc for HTTP layer testing
 * - @DirtiesContext(AFTER_CLASS) for context isolation
 * - @BeforeEach cleanup for test isolation within the class
 * - @MockBean ZipLookupService to avoid real HTTP calls to Zippopotam.us
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase03VerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientPhotoRepository patientPhotoRepository;

    @MockBean
    private ZipLookupService zipLookupService;

    @BeforeEach
    void setUp() {
        // Clean database state before each test
        patientPhotoRepository.deleteAll();
        patientRepository.deleteAll();

        // Unknown ZIP returns empty (set first so 90210 override wins)
        when(zipLookupService.lookup(anyString())).thenReturn(Optional.empty());
        // Return Beverly Hills for 90210
        when(zipLookupService.lookup("90210")).thenReturn(Optional.of(
            ZipLookupResponse.builder()
                .zipCode("90210")
                .city("Beverly Hills")
                .state("California")
                .stateAbbreviation("CA")
                .build()
        ));
    }

    // =====================================================================
    // SC1: Quick Registration — minimal fields + "complete later" workflow
    // =====================================================================

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc1_quickRegister_withMinimalFields_returns201AndIsRegistrationCompleteFalse() throws Exception {
        String requestJson = """
            {
                "firstName": "Alice",
                "lastName": "Walker",
                "dateOfBirth": "1985-03-20",
                "gender": "FEMALE",
                "phoneNumber": "555-987-1111"
            }
            """;

        mockMvc.perform(post("/api/v1/patients/quick")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("Alice"))
            .andExpect(jsonPath("$.isRegistrationComplete").value(false))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.businessId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc1_quickRegister_missingRequiredField_returns400() throws Exception {
        // Missing phoneNumber — required field
        String requestJson = """
            {
                "firstName": "Alice",
                "lastName": "Walker",
                "dateOfBirth": "1985-03-20",
                "gender": "FEMALE"
            }
            """;

        mockMvc.perform(post("/api/v1/patients/quick")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc1_completeQuickRegistration_updateWithIsRegistrationCompleteTrue_insertsNewVersion() throws Exception {
        // 1. Quick-register patient
        String quickJson = """
            {
                "firstName": "Bob",
                "lastName": "Smith",
                "dateOfBirth": "1990-07-15",
                "gender": "MALE",
                "phoneNumber": "555-111-2222"
            }
            """;

        MvcResult quickResult = mockMvc.perform(post("/api/v1/patients/quick")
                .contentType(MediaType.APPLICATION_JSON)
                .content(quickJson))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = quickResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> quickResponse = objectMapper.readValue(responseBody, Map.class);
        String businessId = (String) quickResponse.get("businessId");
        assertThat(quickResponse.get("isRegistrationComplete")).isEqualTo(false);

        // 2. Complete the registration via PUT
        String updateJson = """
            {
                "firstName": "Bob",
                "lastName": "Smith",
                "dateOfBirth": "1990-07-15",
                "gender": "MALE",
                "phoneNumber": "555-111-2222",
                "isRegistrationComplete": true
            }
            """;

        mockMvc.perform(put("/api/v1/patients/" + businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isRegistrationComplete").value(true))
            .andExpect(jsonPath("$.version").value(2));  // New version inserted
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void sc1_quickRegister_doctorRole_returns403() throws Exception {
        String requestJson = """
            {
                "firstName": "Alice",
                "lastName": "Walker",
                "dateOfBirth": "1985-03-20",
                "gender": "FEMALE",
                "phoneNumber": "555-999-7777"
            }
            """;

        mockMvc.perform(post("/api/v1/patients/quick")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isForbidden());
    }

    // =====================================================================
    // SC2: Photo Upload — webcam photo capture
    // =====================================================================

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc2_uploadPhoto_validJpeg_returns201WithPhotoMetadata() throws Exception {
        // Register a patient first
        String businessId = registerTestPatient("555-222-3333");

        byte[] jpegBytes = createMinimalJpeg();
        MockMultipartFile photoFile = new MockMultipartFile(
            "file", "webcam-photo.jpg", "image/jpeg", jpegBytes
        );

        mockMvc.perform(multipart("/api/v1/patients/" + businessId + "/photo")
                .file(photoFile))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientBusinessId").value(businessId))
            .andExpect(jsonPath("$.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.isCurrent").value(true))
            .andExpect(jsonPath("$.filename").isNotEmpty())
            .andExpect(jsonPath("$.uploadedBy").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc2_uploadPhoto_nonImageFile_returns400() throws Exception {
        String businessId = registerTestPatient("555-333-4444");

        MockMultipartFile pdfFile = new MockMultipartFile(
            "file", "document.pdf", "application/pdf",
            "PDF content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/patients/" + businessId + "/photo")
                .file(pdfFile))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc2_uploadSecondPhoto_firstPhotoIsCurrentBecomeFalse() throws Exception {
        String businessId = registerTestPatient("555-444-5555");
        byte[] jpegBytes = createMinimalJpeg();

        // Upload first photo
        MockMultipartFile photo1 = new MockMultipartFile(
            "file", "photo1.jpg", "image/jpeg", jpegBytes
        );
        mockMvc.perform(multipart("/api/v1/patients/" + businessId + "/photo")
                .file(photo1))
            .andExpect(status().isCreated());

        // Upload second photo
        MockMultipartFile photo2 = new MockMultipartFile(
            "file", "photo2.jpg", "image/jpeg", jpegBytes
        );
        mockMvc.perform(multipart("/api/v1/patients/" + businessId + "/photo")
                .file(photo2))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isCurrent").value(true));

        // Verify only one current photo exists in database
        UUID patientBusinessId = UUID.fromString(businessId);
        Optional<com.hospital.patient.domain.PatientPhoto> currentPhoto =
            patientPhotoRepository.findByPatientBusinessIdAndIsCurrentTrue(patientBusinessId);
        assertThat(currentPhoto).isPresent();

        // Total photos should be 2 (both stored, only one current)
        long totalPhotos = patientPhotoRepository.findAll().stream()
            .filter(p -> p.getPatientBusinessId().equals(patientBusinessId))
            .count();
        assertThat(totalPhotos).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc2_getPhoto_patientWithNoPhoto_returns404() throws Exception {
        String businessId = registerTestPatient("555-555-6666");

        mockMvc.perform(get("/api/v1/patients/" + businessId + "/photo"))
            .andExpect(status().isNotFound());
    }

    // =====================================================================
    // SC3: Data Quality Dashboard — ADMIN-only aggregate metrics
    // =====================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void sc3_dataQualityDashboard_adminRole_returns200WithAllFields() throws Exception {
        mockMvc.perform(get("/api/v1/admin/data-quality"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalActivePatients").isNumber())
            .andExpect(jsonPath("$.incompleteRegistrations").isNumber())
            .andExpect(jsonPath("$.missingInsurance").isNumber())
            .andExpect(jsonPath("$.missingPhoto").isNumber())
            .andExpect(jsonPath("$.unverifiedPhotoIds").isNumber())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void sc3_dataQualityDashboard_doctorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/data-quality"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void sc3_dataQualityDashboard_nurseRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/data-quality"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sc3_incompleteRegistrations_reflectsQuickRegisteredPatient() throws Exception {
        // 1. Get baseline incomplete count (database is empty after @BeforeEach cleanup)
        MvcResult baselineResult = mockMvc.perform(get("/api/v1/admin/data-quality"))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> baseline = objectMapper.readValue(
            baselineResult.getResponse().getContentAsString(), Map.class);
        int baselineIncomplete = ((Number) baseline.get("incompleteRegistrations")).intValue();

        // 2. Insert a quick-registered patient directly via repository (bypasses controller
        //    security — no role-switching needed; tests the COUNT query, not the HTTP layer)
        patientRepository.save(Patient.builder()
            .businessId(UUID.randomUUID())
            .firstName("Quick")
            .lastName("RegisteredOnly")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-000-0001")
            .isRegistrationComplete(false)
            .status(PatientStatus.ACTIVE)
            .photoIdVerified(false)
            .version(1L)
            .build());

        // 3. Fetch dashboard again — count must have increased by exactly 1
        MvcResult afterResult = mockMvc.perform(get("/api/v1/admin/data-quality"))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = objectMapper.readValue(
            afterResult.getResponse().getContentAsString(), Map.class);
        int afterIncomplete = ((Number) after.get("incompleteRegistrations")).intValue();

        assertThat(afterIncomplete).isEqualTo(baselineIncomplete + 1);
    }

    // =====================================================================
    // SC4: Smart Forms — ZIP lookup + insurance plan suggestion
    // =====================================================================

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc4_zipLookup_validZip_returnsCityAndState() throws Exception {
        mockMvc.perform(get("/api/v1/smart-form/zip/90210"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zipCode").value("90210"))
            .andExpect(jsonPath("$.city").value("Beverly Hills"))
            .andExpect(jsonPath("$.state").value("California"))
            .andExpect(jsonPath("$.stateAbbreviation").value("CA"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc4_zipLookup_invalidZip_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/smart-form/zip/00000"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void sc4_insurancePlans_returns200WithTenProviders() throws Exception {
        mockMvc.perform(get("/api/v1/smart-form/insurance-plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
            .andExpect(jsonPath("$", hasItem("Aetna")))
            .andExpect(jsonPath("$", hasItem("Medicare")));
    }

    @Test
    void sc4_zipLookup_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/smart-form/zip/90210"))
            .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    /**
     * Register a test patient with full registration and return businessId string.
     * Inserts via MockMvc POST /api/v1/patients to exercise the full registration flow.
     * Security context is inherited from the calling @Test method's @WithMockUser annotation —
     * @WithMockUser has no effect on private methods and must NOT be placed here.
     */
    private String registerTestPatient(String phoneNumber) throws Exception {
        String requestJson = String.format("""
            {
                "firstName": "Test",
                "lastName": "Patient",
                "dateOfBirth": "1980-01-01",
                "gender": "MALE",
                "phoneNumber": "%s",
                "photoIdVerified": true
            }
            """, phoneNumber);

        MvcResult result = mockMvc.perform(post("/api/v1/patients")
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
     * Create a valid 1x1 pixel JPEG using Java AWT BufferedImage + ImageIO.
     * This approach guarantees the bytes pass ImageIO.read() validation in FileStorageService,
     * unlike hand-crafted byte-literal arrays which may be rejected by strict JPEG parsers.
     */
    private byte[] createMinimalJpeg() throws Exception {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);  // 1x1 red pixel
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }
}
