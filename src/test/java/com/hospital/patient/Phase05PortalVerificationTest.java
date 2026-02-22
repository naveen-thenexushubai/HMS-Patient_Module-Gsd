package com.hospital.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.patient.infrastructure.ConsentRepository;
import com.hospital.portal.infrastructure.PatientCredentialRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 5 integration verification tests — Patient Portal self-service.
 * Covers 5 success criteria:
 *   SC1: Portal registration (valid + wrong DOB + duplicate email)
 *   SC2: Portal login → JWT with ROLE_PATIENT; invalid PIN rejected
 *   SC3: GET /me with patient JWT → 200; with staff JWT → 403
 *   SC4: PUT /me/contact → event-sourced update (version increments)
 *   SC5: Patient self-signs HIPAA consent via portal
 *
 * Portal endpoints use real JWT flow: register → login → use token.
 * Staff operations use per-request SecurityMockMvcRequestPostProcessors.user().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase05PortalVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientCredentialRepository credentialRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @BeforeEach
    void setUp() {
        consentRepository.deleteAll();
        credentialRepository.deleteAll();
        patientRepository.deleteAll();
    }

    // =====================================================================
    // SC1: Portal registration — valid, wrong DOB, duplicate email
    // =====================================================================

    @Test
    void sc1_portalRegister_validPatientIdAndDob_returns201() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0001");

        String registerJson = """
            {
                "patientId": "%s",
                "dateOfBirth": "1985-06-15",
                "email": "patient1@portal.test",
                "pin": "1234"
            }
            """.formatted(patient.get("patientId"));

        mockMvc.perform(post("/api/portal/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void sc1_portalRegister_wrongDateOfBirth_returns400() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0002");

        // Wrong DOB (patient's actual DOB is 1985-06-15 as set in registerAndGetIds)
        String registerJson = """
            {
                "patientId": "%s",
                "dateOfBirth": "1990-01-01",
                "email": "patient2@portal.test",
                "pin": "5678"
            }
            """.formatted(patient.get("patientId"));

        mockMvc.perform(post("/api/portal/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sc1_portalRegister_duplicateEmail_returns400() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0003");
        String patientId = patient.get("patientId");

        // Register first account
        String firstJson = """
            {
                "patientId": "%s",
                "dateOfBirth": "1985-06-15",
                "email": "duplicate@portal.test",
                "pin": "1234"
            }
            """.formatted(patientId);

        mockMvc.perform(post("/api/portal/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstJson))
            .andExpect(status().isCreated());

        // Register second patient with same email
        Map<String, String> patient2 = registerAndGetIds("555-800-0004");

        String secondJson = """
            {
                "patientId": "%s",
                "dateOfBirth": "1985-06-15",
                "email": "duplicate@portal.test",
                "pin": "9999"
            }
            """.formatted(patient2.get("patientId"));

        mockMvc.perform(post("/api/portal/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondJson))
            .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // SC2: Portal login — valid credentials return JWT; wrong PIN rejected
    // =====================================================================

    @Test
    void sc2_portalLogin_validCredentials_returnsJwtWithPatientRole() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0005");
        registerPortalAccount(patient.get("patientId"), "login1@portal.test", "4321");

        String loginJson = """
            {
                "email": "login1@portal.test",
                "pin": "4321"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/portal/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.role").value("PATIENT"))
            .andExpect(jsonPath("$.patientBusinessId").isNotEmpty())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        String token = (String) response.get("token");
        assertThat(token).isNotBlank();
    }

    @Test
    void sc2_portalLogin_wrongPin_returns400() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0006");
        registerPortalAccount(patient.get("patientId"), "login2@portal.test", "1111");

        String loginJson = """
            {
                "email": "login2@portal.test",
                "pin": "9999"
            }
            """;

        mockMvc.perform(post("/api/portal/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
            .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // SC3: GET /me — patient JWT returns 200; staff JWT returns 403
    // =====================================================================

    @Test
    void sc3_getOwnProfile_withPatientJwt_returns200WithPatientData() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0007");
        registerPortalAccount(patient.get("patientId"), "me1@portal.test", "2222");
        String token = loginAndGetToken("me1@portal.test", "2222");

        mockMvc.perform(get("/api/portal/v1/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessId").value(patient.get("businessId")))
            .andExpect(jsonPath("$.firstName").value("Portal"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void sc3_getOwnProfile_withStaffJwt_returns403() throws Exception {
        // Staff user (RECEPTIONIST role) cannot access patient portal endpoints
        mockMvc.perform(get("/api/portal/v1/me")
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isForbidden());
    }

    // =====================================================================
    // SC4: PUT /me/contact — updates phone number, creates new patient version
    // =====================================================================

    @Test
    void sc4_updateOwnContact_updatesPhoneNumber_createsNewPatientVersion() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0008");
        registerPortalAccount(patient.get("patientId"), "contact@portal.test", "3333");
        String token = loginAndGetToken("contact@portal.test", "3333");

        // Initial version should be 1
        String businessId = patient.get("businessId");
        mockMvc.perform(get("/api/v1/patients/" + businessId)
                .with(user("receptionist").roles("RECEPTIONIST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        // Update contact via portal
        String contactJson = """
            {
                "phoneNumber": "999-888-7777"
            }
            """;

        mockMvc.perform(put("/api/portal/v1/me/contact")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contactJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phoneNumber").value("999-888-7777"))
            .andExpect(jsonPath("$.version").value(2));
    }

    // =====================================================================
    // SC5: Patient self-signs HIPAA consent via portal
    // =====================================================================

    @Test
    void sc5_selfSignHipaaConsent_returns200WithSignedStatus() throws Exception {
        Map<String, String> patient = registerAndGetIds("555-800-0009");
        registerPortalAccount(patient.get("patientId"), "consent@portal.test", "5555");
        String token = loginAndGetToken("consent@portal.test", "5555");

        mockMvc.perform(post("/api/portal/v1/me/consents/HIPAA_NOTICE/sign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consentType").value("HIPAA_NOTICE"))
            .andExpect(jsonPath("$.status").value("SIGNED"))
            .andExpect(jsonPath("$.signedAt").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty()); // HIPAA_NOTICE expires in 1 year
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    /**
     * Register a test patient via the staff API and return a map with businessId and patientId.
     * Uses RECEPTIONIST per-request auth.
     */
    private Map<String, String> registerAndGetIds(String phoneNumber) throws Exception {
        String requestJson = """
            {
                "firstName": "Portal",
                "lastName": "TestUser",
                "dateOfBirth": "1985-06-15",
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

        return Map.of(
            "businessId", (String) response.get("businessId"),
            "patientId", (String) response.get("patientId")
        );
    }

    /**
     * Register a portal account for the given patientId.
     * Uses the public portal auth endpoint (no staff auth required).
     */
    private void registerPortalAccount(String patientId, String email, String pin) throws Exception {
        String registerJson = """
            {
                "patientId": "%s",
                "dateOfBirth": "1985-06-15",
                "email": "%s",
                "pin": "%s"
            }
            """.formatted(patientId, email, pin);

        mockMvc.perform(post("/api/portal/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isCreated());
    }

    /**
     * Login to the patient portal and return the JWT token string.
     */
    private String loginAndGetToken(String email, String pin) throws Exception {
        String loginJson = """
            {
                "email": "%s",
                "pin": "%s"
            }
            """.formatted(email, pin);

        MvcResult result = mockMvc.perform(post("/api/portal/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("token");
    }
}
