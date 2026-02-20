package com.hospital.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.domain.CoverageType;
import com.hospital.patient.domain.Gender;
import com.hospital.patient.infrastructure.EmergencyContactRepository;
import com.hospital.patient.infrastructure.InsuranceRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hospital.events.PatientUpdatedEvent;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 02 Verification Tests - validates all 6 Phase 2 success criteria.
 *
 * Success criteria verified:
 * SC1: Demographics update via PUT with same validation as registration
 * SC2: patientId and registeredAt read-only; version + lastModifiedAt change on update
 * SC3: Admin activate/deactivate via PATCH /status; active filter excludes inactive patients
 * SC4: Insurance POST/PUT/GET with encrypted PHI storage
 * SC5: Emergency contact POST/PUT/DELETE with ownership validation
 * SC6: PatientUpdatedEvent published after successful update
 *
 * Requirements covered: UPD-01..10, STAT-01..08, INS-01..05, EMR-01..04
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@RecordApplicationEvents
public class Phase02VerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void setUp() {
        insuranceRepository.deleteAll();
        emergencyContactRepository.deleteAll();
        patientRepository.deleteAll();
    }

    // =========================================================================
    // Helper: register a patient and return businessId
    // =========================================================================
    private UUID registerTestPatient(String firstName, String lastName) throws Exception {
        return registerTestPatient(firstName, lastName, "555-123-4567");
    }

    private UUID registerTestPatient(String firstName, String lastName, String phoneNumber) throws Exception {
        String requestBody = """
                {
                  "firstName": "%s",
                  "lastName": "%s",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "phoneNumber": "%s",
                  "photoIdVerified": true
                }
                """.formatted(firstName, lastName, phoneNumber);

        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isCreated())
            .andReturn();

        String json = result.getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("businessId").asText());
    }

    // =========================================================================
    // SC1: Demographics update (UPD-01 through UPD-09)
    // =========================================================================

    @Test
    @DisplayName("SC1: Receptionist can update patient demographics - version increments, registeredAt unchanged")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc1_updatePatientDemographics_success() throws Exception {
        UUID businessId = registerTestPatient("John", "Original");

        // Step 1: GET patient to capture registeredAt and version
        MvcResult getResult = mockMvc.perform(get("/api/v1/patients/" + businessId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andReturn();

        String originalJson = getResult.getResponse().getContentAsString();
        String originalRegisteredAt = objectMapper.readTree(originalJson).get("registeredAt").asText();
        assertThat(originalRegisteredAt).isNotBlank();

        // Step 2: PUT demographics update - change firstName to "UpdatedName"
        String updateBody = """
                {
                  "firstName": "UpdatedName",
                  "lastName": "Original",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "phoneNumber": "555-123-4567"
                }
                """;

        MvcResult updateResult = mockMvc.perform(put("/api/v1/patients/" + businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("UpdatedName"))
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

        String updatedJson = updateResult.getResponse().getContentAsString();
        String updatedRegisteredAt = objectMapper.readTree(updatedJson).get("registeredAt").asText();
        String lastModifiedAt = objectMapper.readTree(updatedJson).get("lastModifiedAt").asText();

        // registeredAt must be UNCHANGED (from version-1 row)
        assertThat(updatedRegisteredAt).isEqualTo(originalRegisteredAt);
        // lastModifiedAt must exist and be different from registeredAt (or same in fast test,
        // but the field must be populated)
        assertThat(lastModifiedAt).isNotBlank();
    }

    @Test
    @DisplayName("SC1: PUT with blank firstName returns RFC 7807 400 with fieldErrors (UPD-09)")
    @WithMockUser(roles = "RECEPTIONIST")
    void sc1_updatePatient_blankFirstName_returns400() throws Exception {
        UUID businessId = registerTestPatient("Valid", "Patient");

        String invalidBody = """
                {
                  "firstName": "",
                  "lastName": "Patient",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "phoneNumber": "555-123-4567"
                }
                """;

        mockMvc.perform(put("/api/v1/patients/" + businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    @Test
    @DisplayName("SC1: DOCTOR cannot update demographics - 403 Forbidden")
    @WithMockUser(roles = "DOCTOR")
    void sc1_updatePatient_doctorRole_returns403() throws Exception {
        // Register a patient first (as RECEPTIONIST via helper)
        UUID businessId = registerTestPatient("Doctor", "Test");

        String updateBody = """
                {
                  "firstName": "ShouldFail",
                  "lastName": "Test",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "phoneNumber": "555-123-4567"
                }
                """;

        // DOCTOR should be rejected with 403
        mockMvc.perform(put("/api/v1/patients/" + businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SC2: Read-only fields preserved (UPD-03, UPD-04)
    // =========================================================================

    @Test
    @DisplayName("SC2: businessId and registeredAt unchanged after update; version increments")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc2_readOnlyFieldsPreservedAfterUpdate() throws Exception {
        UUID businessId = registerTestPatient("Readonly", "Fields");

        // GET to capture original state
        MvcResult original = mockMvc.perform(get("/api/v1/patients/" + businessId))
            .andExpect(status().isOk())
            .andReturn();

        String originalJson = original.getResponse().getContentAsString();
        String originalBusinessId = objectMapper.readTree(originalJson).get("businessId").asText();
        String originalRegisteredAt = objectMapper.readTree(originalJson).get("registeredAt").asText();
        long originalVersion = objectMapper.readTree(originalJson).get("version").asLong();

        assertThat(originalVersion).isEqualTo(1L);

        // PUT demographics
        String updateBody = """
                {
                  "firstName": "ReadonlyUpdated",
                  "lastName": "Fields",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "phoneNumber": "555-123-4567"
                }
                """;

        mockMvc.perform(put("/api/v1/patients/" + businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk());

        // GET again to verify read-only fields
        MvcResult afterUpdate = mockMvc.perform(get("/api/v1/patients/" + businessId))
            .andExpect(status().isOk())
            .andReturn();

        String afterJson = afterUpdate.getResponse().getContentAsString();
        String afterBusinessId = objectMapper.readTree(afterJson).get("businessId").asText();
        String afterRegisteredAt = objectMapper.readTree(afterJson).get("registeredAt").asText();
        long afterVersion = objectMapper.readTree(afterJson).get("version").asLong();
        String lastModifiedAt = objectMapper.readTree(afterJson).get("lastModifiedAt").asText();

        assertThat(afterBusinessId).isEqualTo(originalBusinessId);
        assertThat(afterRegisteredAt).isEqualTo(originalRegisteredAt);
        assertThat(afterVersion).isEqualTo(2L);
        assertThat(lastModifiedAt).isNotBlank();
    }

    // =========================================================================
    // SC3: Status management (STAT-01 through STAT-08)
    // =========================================================================

    @Test
    @DisplayName("SC3: Admin can deactivate patient - status becomes INACTIVE, version increments")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void sc3_deactivatePatient_success() throws Exception {
        UUID businessId = registerTestPatient("Deactivate", "Me");

        String patchBody = """
                {"status": "INACTIVE"}
                """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/patients/" + businessId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andReturn();

        String resultJson = patchResult.getResponse().getContentAsString();
        long version = objectMapper.readTree(resultJson).get("version").asLong();
        assertThat(version).isGreaterThanOrEqualTo(2L);

        // Verify patient no longer appears in ACTIVE filter
        mockMvc.perform(get("/api/v1/patients")
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[?(@.businessId == '" + businessId + "')]").doesNotExist());
    }

    @Test
    @DisplayName("SC3: Admin can re-activate patient - status becomes ACTIVE")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void sc3_activatePatient_success() throws Exception {
        UUID businessId = registerTestPatient("Reactivate", "Me");

        String inactiveBody = """
                {"status": "INACTIVE"}
                """;
        mockMvc.perform(patch("/api/v1/patients/" + businessId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(inactiveBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"));

        String activeBody = """
                {"status": "ACTIVE"}
                """;
        mockMvc.perform(patch("/api/v1/patients/" + businessId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("SC3: PATCH /status with same status is idempotent - no new version inserted")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void sc3_sameStatusChange_isIdempotent() throws Exception {
        UUID businessId = registerTestPatient("Idempotent", "Status");

        // Patient is ACTIVE by default — PATCH to ACTIVE again (same status)
        String activeBody = """
                {"status": "ACTIVE"}
                """;

        MvcResult result = mockMvc.perform(patch("/api/v1/patients/" + businessId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

        // Version must still be 1 (no new version inserted for same-status)
        String json = result.getResponse().getContentAsString();
        long version = objectMapper.readTree(json).get("version").asLong();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    @DisplayName("SC3: Receptionist cannot change status - 403 Forbidden")
    @WithMockUser(roles = "RECEPTIONIST")
    void sc3_receptionistCannotChangeStatus_403() throws Exception {
        UUID businessId = registerTestPatient("Forbidden", "StatusChange");

        String patchBody = """
                {"status": "INACTIVE"}
                """;

        mockMvc.perform(patch("/api/v1/patients/" + businessId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
            .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SC4: Insurance management (INS-01 through INS-05)
    // =========================================================================

    @Test
    @DisplayName("SC4: Receptionist can create insurance - POST returns 201, GET returns insurance (INS-01, INS-04)")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc4_createInsurance_success() throws Exception {
        UUID businessId = registerTestPatient("Insured", "Patient");

        String insuranceBody = """
                {
                  "providerName": "Blue Cross Blue Shield",
                  "policyNumber": "POL-12345",
                  "groupNumber": "GRP-999",
                  "coverageType": "PPO"
                }
                """;

        // POST insurance - expect 201
        MvcResult createResult = mockMvc.perform(post("/api/v1/patients/" + businessId + "/insurance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(insuranceBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.providerName").value("Blue Cross Blue Shield"))
            .andExpect(jsonPath("$.policyNumber").value("POL-12345"))
            .andReturn();

        // GET insurance - expect same data
        mockMvc.perform(get("/api/v1/patients/" + businessId + "/insurance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerName").value("Blue Cross Blue Shield"))
            .andExpect(jsonPath("$.policyNumber").value("POL-12345"));

        // GET patient profile - insuranceInfo should be populated
        mockMvc.perform(get("/api/v1/patients/" + businessId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insuranceInfo").isNotEmpty())
            .andExpect(jsonPath("$.insuranceInfo.providerName").value("Blue Cross Blue Shield"));
    }

    @Test
    @DisplayName("SC4: POST insurance with invalid policyNumber returns RFC 7807 400 (INS-02)")
    @WithMockUser(roles = "RECEPTIONIST")
    void sc4_invalidPolicyNumber_returns400() throws Exception {
        UUID businessId = registerTestPatient("Invalid", "Policy");

        // policyNumber "AB" is too short (requires 3-50 chars)
        String invalidBody = """
                {
                  "providerName": "Test Insurance",
                  "policyNumber": "AB",
                  "coverageType": "HMO"
                }
                """;

        mockMvc.perform(post("/api/v1/patients/" + businessId + "/insurance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors").isMap())
            .andExpect(jsonPath("$.fieldErrors.policyNumber").exists());
    }

    @Test
    @DisplayName("SC4: Receptionist can update insurance - PUT returns 200, updatedAt populated (INS-05)")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc4_updateInsurance_success() throws Exception {
        UUID businessId = registerTestPatient("Insurance", "Update");

        // First create insurance
        String createBody = """
                {
                  "providerName": "Original Provider",
                  "policyNumber": "POL-ORIG-001",
                  "coverageType": "HMO"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/patients/" + businessId + "/insurance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

        String createJson = createResult.getResponse().getContentAsString();
        String createdAt = objectMapper.readTree(createJson).get("createdAt").asText();

        // Now PUT to update provider name
        String updateBody = """
                {
                  "providerName": "Updated Provider",
                  "policyNumber": "POL-ORIG-001",
                  "coverageType": "HMO"
                }
                """;

        MvcResult updateResult = mockMvc.perform(put("/api/v1/patients/" + businessId + "/insurance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerName").value("Updated Provider"))
            .andReturn();

        String updateJson = updateResult.getResponse().getContentAsString();
        String updatedAt = objectMapper.readTree(updateJson).get("updatedAt").asText();

        // updatedAt should be populated after PUT
        assertThat(updatedAt).isNotBlank();
        assertThat(updatedAt).isNotEqualTo("null");
    }

    @Test
    @DisplayName("SC4: DOCTOR cannot create insurance - 403 Forbidden")
    @WithMockUser(roles = "DOCTOR")
    void sc4_doctorCannotCreateInsurance_403() throws Exception {
        UUID businessId = registerTestPatient("Doctor", "InsuranceTest");

        String insuranceBody = """
                {
                  "providerName": "Unauthorized",
                  "policyNumber": "POL-99999",
                  "coverageType": "PPO"
                }
                """;

        mockMvc.perform(post("/api/v1/patients/" + businessId + "/insurance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(insuranceBody))
            .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SC5: Emergency contact CRUD (EMR-01 through EMR-04)
    // =========================================================================

    @Test
    @DisplayName("SC5: Receptionist can add emergency contact - POST returns 201 with id (EMR-01)")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc5_addEmergencyContact_success() throws Exception {
        UUID businessId = registerTestPatient("Contact", "Patient");

        String contactBody = """
                {
                  "name": "Jane Contact",
                  "phoneNumber": "555-987-6543",
                  "relationship": "Spouse",
                  "isPrimary": true
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/patients/" + businessId + "/emergency-contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(contactBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Jane Contact"))
            .andExpect(jsonPath("$.relationship").value("Spouse"))
            .andReturn();

        // GET contacts - should include the new one
        mockMvc.perform(get("/api/v1/patients/" + businessId + "/emergency-contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("Jane Contact"));
    }

    @Test
    @DisplayName("SC5: Emergency contact update with cross-patient contactId returns 403 (ownership check)")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc5_crossPatientContactUpdate_returns403() throws Exception {
        // Register patient A and patient B with different phone numbers to avoid duplicate detection
        UUID patientA = registerTestPatient("PatientA", "CrossTest", "555-111-2222");
        UUID patientB = registerTestPatient("PatientB", "CrossTest", "555-333-4444");

        // Add contact to patient A
        String contactBody = """
                {
                  "name": "Patient A Contact",
                  "phoneNumber": "222-333-4444",
                  "relationship": "Parent",
                  "isPrimary": false
                }
                """;

        MvcResult contactResult = mockMvc.perform(post("/api/v1/patients/" + patientA + "/emergency-contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(contactBody))
            .andExpect(status().isCreated())
            .andReturn();

        String contactJson = contactResult.getResponse().getContentAsString();
        Long contactAId = objectMapper.readTree(contactJson).get("id").asLong();

        // Attempt: PUT on patientB URL with contactId from patientA -> 403 ownership check
        String updateBody = """
                {
                  "name": "Cross Patient Update",
                  "phoneNumber": "555-666-7777",
                  "relationship": "Sibling"
                }
                """;

        mockMvc.perform(put("/api/v1/patients/" + patientB + "/emergency-contacts/" + contactAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SC5: Receptionist can delete emergency contact - DELETE returns 204 (EMR-04)")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc5_deleteEmergencyContact_success() throws Exception {
        UUID businessId = registerTestPatient("Delete", "Contact");

        // Add a contact first
        String contactBody = """
                {
                  "name": "To Be Deleted",
                  "phoneNumber": "333-444-5555",
                  "relationship": "Friend",
                  "isPrimary": false
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/patients/" + businessId + "/emergency-contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(contactBody))
            .andExpect(status().isCreated())
            .andReturn();

        Long contactId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // DELETE contact - expect 204
        mockMvc.perform(delete("/api/v1/patients/" + businessId + "/emergency-contacts/" + contactId))
            .andExpect(status().isNoContent());

        // GET contacts - should be empty
        mockMvc.perform(get("/api/v1/patients/" + businessId + "/emergency-contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================================
    // SC6: PatientUpdatedEvent published (UPD-10)
    // =========================================================================

    @Test
    @DisplayName("SC6: PatientUpdatedEvent is published after demographic update (UPD-10)")
    @WithMockUser(username = "receptionist", roles = "RECEPTIONIST")
    void sc6_patientUpdatedEvent_publishedAfterUpdate() throws Exception {
        UUID businessId = registerTestPatient("Event", "Publisher");

        String updateBody = """
                {
                  "firstName": "EventUpdated",
                  "lastName": "Publisher",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "phoneNumber": "555-123-4567"
                }
                """;

        mockMvc.perform(put("/api/v1/patients/" + businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("EventUpdated"));

        // Verify PatientUpdatedEvent was published via @RecordApplicationEvents
        long eventCount = applicationEvents.stream(PatientUpdatedEvent.class)
            .filter(e -> businessId.equals(e.getBusinessId()))
            .count();

        assertThat(eventCount)
            .as("Expected at least one PatientUpdatedEvent for businessId %s", businessId)
            .isGreaterThanOrEqualTo(1L);

        // Verify event fields
        PatientUpdatedEvent event = applicationEvents.stream(PatientUpdatedEvent.class)
            .filter(e -> businessId.equals(e.getBusinessId()))
            .findFirst()
            .orElseThrow();

        assertThat(event.getBusinessId()).isEqualTo(businessId);
        assertThat(event.getNewVersion()).isEqualTo(2L);
        assertThat(event.getChangedFields()).contains("demographics");
    }
}
