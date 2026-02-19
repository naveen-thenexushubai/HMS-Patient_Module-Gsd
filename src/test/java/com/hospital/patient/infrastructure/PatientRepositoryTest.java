package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PatientRepository.
 * Tests event-sourced pattern with DISTINCT ON queries.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patientRepository;

    @Test
    void shouldGeneratePatientIdInCorrectFormat() {
        // Given
        Patient patient = createTestPatient("John", "Doe");

        // When
        Patient saved = patientRepository.save(patient);

        // Then
        assertThat(saved.getPatientId()).isNotNull();
        assertThat(saved.getPatientId()).matches("P\\d{9}"); // P + year (4) + sequence (5)
        assertThat(saved.getPatientId()).startsWith("P2026"); // Current year
    }

    @Test
    void shouldFindLatestVersionByBusinessId() {
        // Given: Create patient and two versions
        UUID businessId = UUID.randomUUID();
        Patient v1 = createTestPatientWithBusinessId(businessId, 1L, "John", "Doe");
        Patient v2 = createTestPatientWithBusinessId(businessId, 2L, "John", "Smith");
        patientRepository.save(v1);
        patientRepository.save(v2);

        // When
        Optional<Patient> latest = patientRepository.findLatestVersionByBusinessId(businessId);

        // Then
        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(2L);
        assertThat(latest.get().getLastName()).isEqualTo("Smith");
    }

    @Test
    void shouldFindLatestVersionsByStatus() {
        // Given: Create active and inactive patients
        Patient active1 = createTestPatient("John", "Doe");
        active1.setStatus(PatientStatus.ACTIVE);
        Patient active2 = createTestPatient("Jane", "Smith");
        active2.setStatus(PatientStatus.ACTIVE);
        Patient inactive = createTestPatient("Bob", "Jones");
        inactive.setStatus(PatientStatus.INACTIVE);

        patientRepository.saveAll(List.of(active1, active2, inactive));

        // When
        Slice<Patient> activePatients = patientRepository.findLatestVersionsByStatus(
            "ACTIVE", PageRequest.of(0, 10)
        );

        // Then
        assertThat(activePatients.getContent()).hasSize(2);
        assertThat(activePatients.getContent())
            .allMatch(p -> p.getStatus() == PatientStatus.ACTIVE);
    }

    @Test
    void shouldFindAllVersionsByBusinessId() {
        // Given: Create patient with 3 versions
        UUID businessId = UUID.randomUUID();
        Patient v1 = createTestPatientWithBusinessId(businessId, 1L, "John", "Doe");
        Patient v2 = createTestPatientWithBusinessId(businessId, 2L, "John", "Smith");
        Patient v3 = createTestPatientWithBusinessId(businessId, 3L, "John", "Johnson");
        patientRepository.saveAll(List.of(v1, v2, v3));

        // When
        List<Patient> versions = patientRepository.findAllVersionsByBusinessId(businessId);

        // Then
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersion()).isEqualTo(3L); // DESC order
        assertThat(versions.get(1).getVersion()).isEqualTo(2L);
        assertThat(versions.get(2).getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldCheckIfPatientIdExists() {
        // Given
        Patient patient = createTestPatient("John", "Doe");
        Patient saved = patientRepository.save(patient);

        // When
        boolean exists = patientRepository.existsByPatientId(saved.getPatientId());
        boolean notExists = patientRepository.existsByPatientId("P9999999999");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void shouldFindLatestVersionsByDateOfBirth() {
        // Given: Create patients with same DOB
        LocalDate dob = LocalDate.of(1990, 1, 1);
        Patient p1 = createTestPatient("John", "Doe");
        p1.setDateOfBirth(dob);
        Patient p2 = createTestPatient("Jane", "Smith");
        p2.setDateOfBirth(dob);
        Patient p3 = createTestPatient("Bob", "Jones");
        p3.setDateOfBirth(LocalDate.of(1995, 5, 5));

        patientRepository.saveAll(List.of(p1, p2, p3));

        // When
        List<Patient> sameDobPatients = patientRepository.findLatestVersionsByDateOfBirth(dob);

        // Then
        assertThat(sameDobPatients).hasSize(2);
        assertThat(sameDobPatients).allMatch(p -> p.getDateOfBirth().equals(dob));
    }

    @Test
    void shouldCalculateAgeCorrectly() {
        // Given
        LocalDate dob = LocalDate.now().minusYears(30).minusDays(1);
        Patient patient = createTestPatient("John", "Doe");
        patient.setDateOfBirth(dob);
        Patient saved = patientRepository.save(patient);

        // When
        int age = saved.getAge();

        // Then
        assertThat(age).isEqualTo(30);
    }

    @Test
    void shouldFindByPatientId() {
        // Given
        Patient patient = createTestPatient("John", "Doe");
        Patient saved = patientRepository.save(patient);

        // When
        Optional<Patient> found = patientRepository.findByPatientId(saved.getPatientId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getPatientId()).isEqualTo(saved.getPatientId());
    }

    @Test
    void shouldSetDefaultValuesOnPrePersist() {
        // Given: Patient without businessId, version, or status
        Patient patient = Patient.builder()
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-1234")
            .createdBy("test")
            .build();

        // When
        Patient saved = patientRepository.save(patient);

        // Then
        assertThat(saved.getBusinessId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(PatientStatus.ACTIVE);
    }

    // Helper methods

    private Patient createTestPatient(String firstName, String lastName) {
        return Patient.builder()
            .firstName(firstName)
            .lastName(lastName)
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-" + System.nanoTime() % 10000)
            .email(firstName.toLowerCase() + "@test.com")
            .addressLine1("123 Main St")
            .city("Test City")
            .state("TS")
            .zipCode("12345")
            .status(PatientStatus.ACTIVE)
            .createdBy("test")
            .build();
    }

    private Patient createTestPatientWithBusinessId(UUID businessId, Long version, String firstName, String lastName) {
        return Patient.builder()
            .businessId(businessId)
            .version(version)
            .firstName(firstName)
            .lastName(lastName)
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-" + System.nanoTime() % 10000)
            .email(firstName.toLowerCase() + version + "@test.com")
            .status(PatientStatus.ACTIVE)
            .createdBy("test")
            .build();
    }
}
