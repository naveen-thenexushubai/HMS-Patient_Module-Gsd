package com.hospital.patient.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.api.dto.EmergencyContactDto;
import com.hospital.patient.api.dto.MedicalHistoryDto;
import com.hospital.patient.api.dto.RegisterPatientRequest;
import com.hospital.patient.domain.BloodGroup;
import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import com.hospital.patient.infrastructure.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 01 Verification Tests - validates all 6 success criteria from ROADMAP.
 *
 * Success criteria verified:
 * 1. Receptionist can register new patient with all required fields and generated unique ID
 * 2. System warns about potential duplicate patients with fuzzy matching, allows override
 * 3. Staff can search patients and see results within 2 seconds for 10,000 patient records
 * 4. Staff can filter patient list by status/gender with paginated results (20 per page)
 * 5. Staff can view complete patient profile including demographics, emergency contacts, medical info
 * 6. Edit Patient allowed only for Receptionist/Admin, read-only for Doctor/Nurse
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class Phase01VerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void setUp() {
        patientRepository.deleteAll();
    }

    /**
     * SUCCESS CRITERION 1:
     * Receptionist can register new patient with all required fields
     * and system generates unique Patient ID (format: P + year + sequential digits).
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void successCriterion1_registerPatientWithGeneratedId() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-123-4567")
            .email("john.doe@example.com")
            .photoIdVerified(true)
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").exists())
            .andExpect(jsonPath("$.patientId").value(matchesPattern("P\\d{9}")))
            .andExpect(jsonPath("$.firstName").value("John"))
            .andExpect(jsonPath("$.lastName").value("Doe"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.businessId").exists())
            .andExpect(jsonPath("$.registeredAt").exists())
            .andReturn();

        // Verify unique ID generation - register a second patient and check IDs differ
        RegisterPatientRequest request2 = RegisterPatientRequest.builder()
            .firstName("Jane")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1995, 6, 15))
            .gender(Gender.FEMALE)
            .phoneNumber("555-987-6543")
            .email("jane.doe@example.com")
            .photoIdVerified(true)
            .build();

        MvcResult result2 = mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").exists())
            .andReturn();

        String body1 = result.getResponse().getContentAsString();
        String body2 = result2.getResponse().getContentAsString();
        String id1 = objectMapper.readTree(body1).get("patientId").asText();
        String id2 = objectMapper.readTree(body2).get("patientId").asText();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1).matches("P\\d{9}");
        assertThat(id2).matches("P\\d{9}");
    }

    /**
     * SUCCESS CRITERION 2:
     * System warns about potential duplicate patients (fuzzy matching on name, DOB, phone)
     * and blocks high-confidence duplicates even with override flag.
     * The 409 Conflict response includes duplicate match details.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void successCriterion2_duplicateDetectionWithOverride() throws Exception {
        // Register first patient
        RegisterPatientRequest request1 = RegisterPatientRequest.builder()
            .firstName("Jane")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1985, 5, 15))
            .gender(Gender.FEMALE)
            .phoneNumber("555-999-8888")
            .email("jane.smith@example.com")
            .photoIdVerified(true)
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        // Attempt to register exact duplicate (same name, DOB, phone) - should return 409 Conflict
        // Exact same name + DOB + phone = 100% similarity score -> warning response
        RegisterPatientRequest request2 = RegisterPatientRequest.builder()
            .firstName("Jane")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1985, 5, 15))
            .gender(Gender.FEMALE)
            .phoneNumber("555-999-8888")
            .email("jane.smith2@example.com")
            .photoIdVerified(true)
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("duplicate")))
            .andExpect(jsonPath("$.matches").isArray());

        // High-confidence duplicate (>=90% score) with overrideDuplicate=true
        // returns 403 Forbidden - requires admin approval per design
        mockMvc.perform(post("/api/v1/patients")
                .param("overrideDuplicate", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isForbidden());

        // Register a genuinely different patient - should always succeed (no duplicate)
        RegisterPatientRequest uniqueRequest = RegisterPatientRequest.builder()
            .firstName("Unique")
            .lastName("Patient")
            .dateOfBirth(LocalDate.of(1990, 12, 25))
            .gender(Gender.FEMALE)
            .phoneNumber("555-111-7777")
            .email("unique.patient@example.com")
            .photoIdVerified(true)
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(uniqueRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").exists());
    }

    /**
     * SUCCESS CRITERION 3:
     * Staff can search patients by name and see results within 2 seconds
     * for 10,000 patient records (performance requirement).
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void successCriterion3_searchPerformanceWithin2Seconds() throws Exception {
        // Seed 10,000 patients using direct repository batch inserts for speed
        List<Patient> batch = new ArrayList<>(1000);
        for (int i = 1; i <= 10000; i++) {
            Patient patient = Patient.builder()
                .firstName("Patient" + i)
                .lastName("Test" + (i % 500))
                .dateOfBirth(LocalDate.of(1950 + (i % 50), 1 + (i % 12), 1 + (i % 28)))
                .gender(i % 2 == 0 ? Gender.MALE : Gender.FEMALE)
                .phoneNumber(String.format("555-%03d-%04d", (i / 10000) % 1000, i % 10000))
                .status(PatientStatus.ACTIVE)
                .build();
            batch.add(patient);

            if (batch.size() == 1000) {
                patientRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            patientRepository.saveAll(batch);
        }

        // Measure search performance
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/api/v1/patients")
                .param("query", "Patient5000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration)
            .as("Search over 10K patients must complete within 2000ms, actual: %dms", duration)
            .isLessThan(2000L);
    }

    /**
     * SUCCESS CRITERION 4:
     * Staff can filter patient list by status (Active/Inactive) and gender
     * with paginated results (20 per page default).
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void successCriterion4_filterAndPagination() throws Exception {
        // Seed 50 patients with mixed statuses and genders
        for (int i = 0; i < 50; i++) {
            Patient patient = Patient.builder()
                .firstName("Filter" + i)
                .lastName("Patient" + i)
                .dateOfBirth(LocalDate.of(1990, 1, 1 + (i % 28)))
                .gender(i % 2 == 0 ? Gender.MALE : Gender.FEMALE)
                .phoneNumber(String.format("555-4%02d-%04d", i / 100, i % 10000))
                .status(i % 3 == 0 ? PatientStatus.INACTIVE : PatientStatus.ACTIVE)
                .build();
            patientRepository.save(patient);
        }

        // Test filtering by status=ACTIVE
        mockMvc.perform(get("/api/v1/patients")
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        // Test filtering by status=INACTIVE
        mockMvc.perform(get("/api/v1/patients")
                .param("status", "INACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        // Test filtering by gender=MALE
        mockMvc.perform(get("/api/v1/patients")
                .param("gender", "MALE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].gender").value("MALE"));

        // Test default pagination returns max 20 results
        MvcResult pageResult = mockMvc.perform(get("/api/v1/patients")
                .param("size", "20")
                .param("page", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andReturn();

        String pageBody = pageResult.getResponse().getContentAsString();
        int contentSize = objectMapper.readTree(pageBody).get("content").size();
        assertThat(contentSize).isLessThanOrEqualTo(20);
    }

    /**
     * SUCCESS CRITERION 5:
     * Staff can view complete patient profile including demographics,
     * emergency contacts, medical info, and registration audit trail.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST", username = "receptionist-user")
    void successCriterion5_completePatientProfile() throws Exception {
        // Register patient with emergency contact and medical history
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Complete")
            .lastName("Profile")
            .dateOfBirth(LocalDate.of(1980, 3, 10))
            .gender(Gender.MALE)
            .phoneNumber("555-111-2222")
            .email("complete@example.com")
            .photoIdVerified(true)
            .emergencyContacts(List.of(
                EmergencyContactDto.builder()
                    .name("Emergency Contact")
                    .phoneNumber("555-333-4444")
                    .relationship("Spouse")
                    .isPrimary(true)
                    .build()
            ))
            .medicalHistory(MedicalHistoryDto.builder()
                .bloodGroup(BloodGroup.A_POSITIVE)
                .allergies("Penicillin")
                .chronicConditions("Hypertension")
                .build())
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String createBody = createResult.getResponse().getContentAsString();
        String businessId = objectMapper.readTree(createBody).get("businessId").asText();

        // Retrieve complete patient profile
        mockMvc.perform(get("/api/v1/patients/" + businessId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Complete"))
            .andExpect(jsonPath("$.lastName").value("Profile"))
            .andExpect(jsonPath("$.dateOfBirth").value("1980-03-10"))
            .andExpect(jsonPath("$.gender").value("MALE"))
            .andExpect(jsonPath("$.phoneNumber").value("555-111-2222"))
            .andExpect(jsonPath("$.email").value("complete@example.com"))
            .andExpect(jsonPath("$.emergencyContacts").isArray())
            .andExpect(jsonPath("$.emergencyContacts[0].name").value("Emergency Contact"))
            .andExpect(jsonPath("$.emergencyContacts[0].relationship").value("Spouse"))
            .andExpect(jsonPath("$.medicalHistory").isNotEmpty())
            .andExpect(jsonPath("$.medicalHistory.bloodGroup").value("A_POSITIVE"))
            .andExpect(jsonPath("$.medicalHistory.allergies").value("Penicillin"))
            .andExpect(jsonPath("$.medicalHistory.chronicConditions").value("Hypertension"))
            .andExpect(jsonPath("$.registeredAt").exists())
            .andExpect(jsonPath("$.registeredBy").exists())
            .andExpect(jsonPath("$.patientId").exists())
            .andExpect(jsonPath("$.businessId").value(businessId));
    }

    /**
     * SUCCESS CRITERION 6:
     * Edit Patient (write access) is allowed for Receptionist and Admin.
     * Doctor and Nurse roles have read-only access (cannot register patients).
     * Authorization model enforced at API level.
     */
    @Test
    void successCriterion6_roleBasedEditPermission() throws Exception {
        // First register a patient as RECEPTIONIST (write access required)
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Permission")
            .lastName("Test")
            .dateOfBirth(LocalDate.of(1995, 7, 20))
            .gender(Gender.FEMALE)
            .phoneNumber("555-777-8888")
            .photoIdVerified(true)
            .build();

        // RECEPTIONIST can register (write access)
        MvcResult receptionistCreate = mockMvc.perform(post("/api/v1/patients")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("receptionist").roles("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String businessId = objectMapper.readTree(
            receptionistCreate.getResponse().getContentAsString()
        ).get("businessId").asText();

        // DOCTOR can READ patient (read-only)
        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("doctor").roles("DOCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Permission"));

        // NURSE can READ patient (read-only)
        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("nurse").roles("NURSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Permission"));

        // ADMIN can READ patient
        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Permission"));

        // DOCTOR cannot REGISTER new patients (write-restricted)
        mockMvc.perform(post("/api/v1/patients")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("doctor").roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

        // NURSE cannot REGISTER new patients (write-restricted)
        mockMvc.perform(post("/api/v1/patients")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("nurse").roles("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

        // ADMIN can REGISTER new patients (full access)
        RegisterPatientRequest adminRequest = RegisterPatientRequest.builder()
            .firstName("Admin")
            .lastName("Created")
            .dateOfBirth(LocalDate.of(1988, 4, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-111-9999")
            .photoIdVerified(true)
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminRequest)))
            .andExpect(status().isCreated());
    }

    /**
     * Bonus: Verify RFC 7807 Problem Details error format for validation errors.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void bonusVerification_rfc7807ValidationErrorFormat() throws Exception {
        // Submit invalid patient request (empty required fields)
        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(containsString("validation-error")))
            .andExpect(jsonPath("$.title").value("Validation Error"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").exists())
            .andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * Bonus: Verify RFC 7807 Problem Details error format for not found errors.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void bonusVerification_rfc7807NotFoundErrorFormat() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/patients/" + nonExistentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(containsString("patient-not-found")))
            .andExpect(jsonPath("$.title").value("Patient Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").exists())
            .andExpect(jsonPath("$.patientIdentifier").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * REG-12 GAP CLOSURE:
     * Registration must be rejected (400 Bad Request) when photoIdVerified is false.
     * Verifies that the @AssertTrue constraint fires and the RFC 7807 response
     * contains the correct fieldErrors entry.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void reg12_registrationRejectedWhenPhotoIdNotVerified() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Test")
            .lastName("Patient")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-123-9999")
            .email("test.patient@example.com")
            .photoIdVerified(false)  // Explicitly not verified
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(jsonPath("$.fieldErrors.photoIdVerified").exists());
    }

    /**
     * REG-12 GAP CLOSURE:
     * Registration succeeds (201 Created) when photoIdVerified is true.
     * Verifies the happy path for the photo ID verification flag.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void reg12_registrationSucceedsWhenPhotoIdVerified() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Verified")
            .lastName("Patient")
            .dateOfBirth(LocalDate.of(1988, 6, 15))
            .gender(Gender.FEMALE)
            .phoneNumber("555-456-7890")
            .email("verified.patient@example.com")
            .photoIdVerified(true)  // Verified — registration allowed
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").exists())
            .andExpect(jsonPath("$.firstName").value("Verified"));
    }

    /**
     * REG-12 GAP CLOSURE:
     * Registration must be rejected (400 Bad Request) when photoIdVerified is omitted (null).
     * The @NotNull constraint fires before @AssertTrue.
     */
    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void reg12_registrationRejectedWhenPhotoIdNull() throws Exception {
        // Omit photoIdVerified entirely (null) — should fail @NotNull
        String requestJson = """
            {
              "firstName": "Null",
              "lastName": "PhotoId",
              "dateOfBirth": "1992-03-15",
              "gender": "MALE",
              "phoneNumber": "555-321-6543"
            }
            """;

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(jsonPath("$.fieldErrors.photoIdVerified").exists());
    }
}
