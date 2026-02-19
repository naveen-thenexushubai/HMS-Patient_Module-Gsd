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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
