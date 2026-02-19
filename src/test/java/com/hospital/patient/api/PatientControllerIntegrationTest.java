package com.hospital.patient.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.patient.api.dto.RegisterPatientRequest;
import com.hospital.patient.domain.Gender;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatientControllerIntegrationTest {

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

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldRegisterPatientSuccessfully() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 1, 15))
            .gender(Gender.MALE)
            .phoneNumber("555-123-4567")
            .email("john.doe@example.com")
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").exists())
            .andExpect(jsonPath("$.firstName").value("John"))
            .andExpect(jsonPath("$.lastName").value("Doe"))
            .andExpect(jsonPath("$.age").exists());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldDetectDuplicateAndReturn409() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Jane")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1985, 5, 20))
            .gender(Gender.FEMALE)
            .phoneNumber("555-987-6543")
            .email("jane@example.com")
            .build();

        // First registration
        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Second registration with same details
        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.matches").isArray())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldValidateRequiredFields() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("") // Invalid: empty
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldValidatePhoneNumberFormat() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Test")
            .lastName("User")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("invalid-phone")
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void shouldDenyAccessForUnauthorizedRole() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 1, 15))
            .gender(Gender.MALE)
            .phoneNumber("555-123-4567")
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldSearchPatientsByName() throws Exception {
        // Register a patient first
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Alice")
            .lastName("Johnson")
            .dateOfBirth(LocalDate.of(1985, 3, 10))
            .gender(Gender.FEMALE)
            .phoneNumber("555-111-2222")
            .email("alice@example.com")
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Search for the patient
        mockMvc.perform(get("/api/v1/patients")
                .param("query", "Alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].fullName").value("Alice Johnson"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldSearchWithStatusFilter() throws Exception {
        // Register a patient
        RegisterPatientRequest request = RegisterPatientRequest.builder()
            .firstName("Bob")
            .lastName("Williams")
            .dateOfBirth(LocalDate.of(1990, 7, 25))
            .gender(Gender.MALE)
            .phoneNumber("555-333-4444")
            .build();

        mockMvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Search with status filter
        mockMvc.perform(get("/api/v1/patients")
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void shouldSearchWithPagination() throws Exception {
        // Register multiple patients
        for (int i = 0; i < 3; i++) {
            RegisterPatientRequest request = RegisterPatientRequest.builder()
                .firstName("Patient" + i)
                .lastName("Test")
                .dateOfBirth(LocalDate.of(1990 + i, 1, 1))
                .gender(Gender.MALE)
                .phoneNumber("555-000-000" + i)
                .build();

            mockMvc.perform(post("/api/v1/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        }

        // Search with pagination
        mockMvc.perform(get("/api/v1/patients")
                .param("size", "2")
                .param("page", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void shouldDenySearchWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
            .andExpect(status().isUnauthorized());
    }
}
