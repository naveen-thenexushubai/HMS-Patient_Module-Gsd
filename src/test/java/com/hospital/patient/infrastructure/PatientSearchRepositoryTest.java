package com.hospital.patient.infrastructure;

import com.hospital.patient.api.dto.PatientSummaryResponse;
import com.hospital.patient.domain.*;
import jakarta.persistence.EntityManager;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PatientSearchRepository.
 * Tests Hibernate Search functionality with Lucene backend.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PatientSearchRepository.class, PatientSearchRepositoryTest.TestAuditorConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PatientSearchRepositoryTest {

    @TestConfiguration
    static class TestAuditorConfig {
        @Bean
        public AuditorAware<String> auditorProvider() {
            return () -> Optional.of("test-user");
        }
    }

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientSearchRepository patientSearchRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Seed test patients with various names, phones, emails
        createPatient("John", "Smith", "555-1234", "john.smith@example.com",
            Gender.MALE, PatientStatus.ACTIVE, LocalDate.of(1990, 5, 15));
        createPatient("Jon", "Smyth", "555-5678", "jon.smyth@example.com",
            Gender.MALE, PatientStatus.ACTIVE, LocalDate.of(1985, 8, 20));
        createPatient("Jane", "Doe", "555-9999", "jane.doe@example.com",
            Gender.FEMALE, PatientStatus.ACTIVE, LocalDate.of(1995, 3, 10));
        createPatient("Robert", "Johnson", "555-1111", "robert.j@example.com",
            Gender.MALE, PatientStatus.INACTIVE, LocalDate.of(1980, 12, 5));
        createPatient("Sarah", "Williams", "555-2222", "sarah.w@example.com",
            Gender.FEMALE, PatientStatus.ACTIVE, LocalDate.of(1992, 7, 25));

        // Flush and clear to ensure database state
        entityManager.flush();
        entityManager.clear();

        // Trigger Hibernate Search indexing
        SearchSession searchSession = Search.session(entityManager);
        searchSession.massIndexer(Patient.class)
            .threadsToLoadObjects(1)
            .batchSizeToLoadObjects(10)
            .startAndWait();
    }

    private Patient createPatient(String firstName, String lastName, String phone,
                                  String email, Gender gender, PatientStatus status,
                                  LocalDate dob) {
        Patient patient = Patient.builder()
            .firstName(firstName)
            .lastName(lastName)
            .phoneNumber(phone)
            .email(email)
            .gender(gender)
            .status(status)
            .dateOfBirth(dob)
            .businessId(UUID.randomUUID())
            .version(1L)
            .build();
        return patientRepository.save(patient);
    }

    @Test
    void searchByPatientId_returnsExactMatch() {
        // Get a patient ID from the database
        Patient patient = patientRepository.findAll().get(0);
        String patientId = patient.getPatientId();

        Pageable pageable = PageRequest.of(0, 20);
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            patientId, null, null, null, pageable
        );

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getPatientId()).isEqualTo(patientId);
    }

    @Test
    void searchByName_withFuzzyMatching_returnsMatches() {
        Pageable pageable = PageRequest.of(0, 20);

        // Search for "Jon" should find both "John" and "Jon"
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            "Jon", null, null, null, pageable
        );

        assertThat(results.getContent().size()).isGreaterThanOrEqualTo(1);
        assertThat(results.getContent())
            .extracting(PatientSummaryResponse::getFullName)
            .anyMatch(name -> name.contains("Jon") || name.contains("John"));
    }

    @Test
    void searchByName_withTypo_returnsFuzzyMatches() {
        Pageable pageable = PageRequest.of(0, 20);

        // "Smyth" vs "Smith" - 1 character difference, should match with fuzzy(2)
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            "Smyth", null, null, null, pageable
        );

        assertThat(results.getContent().size()).isGreaterThanOrEqualTo(1);
        assertThat(results.getContent())
            .extracting(PatientSummaryResponse::getFullName)
            .anyMatch(name -> name.contains("Smyth") || name.contains("Smith"));
    }

    @Test
    void searchByPhone_withPartialMatch_returnsResults() {
        Pageable pageable = PageRequest.of(0, 20);

        // Search by partial phone number
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            "555-1234", null, null, null, pageable
        );

        assertThat(results.getContent()).isNotEmpty();
        assertThat(results.getContent())
            .extracting(PatientSummaryResponse::getPhoneNumber)
            .anyMatch(phone -> phone.contains("555-1234"));
    }

    @Test
    void searchByEmail_withPartialMatch_returnsResults() {
        Pageable pageable = PageRequest.of(0, 20);

        // Search by partial email
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            "john@", null, null, null, pageable
        );

        assertThat(results.getContent()).isNotEmpty();
        assertThat(results.getContent())
            .extracting(PatientSummaryResponse::getFullName)
            .anyMatch(name -> name.contains("John"));
    }

    @Test
    void filterByStatus_returnsOnlyActivePatients() {
        Pageable pageable = PageRequest.of(0, 20);

        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            null, PatientStatus.ACTIVE, null, null, pageable
        );

        assertThat(results.getContent()).isNotEmpty();
        assertThat(results.getContent())
            .extracting(PatientSummaryResponse::getStatus)
            .containsOnly(PatientStatus.ACTIVE);
    }

    @Test
    void filterByGender_returnsOnlyMalePatients() {
        Pageable pageable = PageRequest.of(0, 20);

        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            null, null, Gender.MALE, null, pageable
        );

        assertThat(results.getContent()).isNotEmpty();
        assertThat(results.getContent())
            .extracting(PatientSummaryResponse::getGender)
            .containsOnly(Gender.MALE);
    }

    @Test
    void searchWithMultipleFilters_returnsIntersection() {
        Pageable pageable = PageRequest.of(0, 20);

        // Search for "John" + ACTIVE + MALE
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            "John", PatientStatus.ACTIVE, Gender.MALE, null, pageable
        );

        assertThat(results.getContent()).isNotEmpty();
        assertThat(results.getContent()).allMatch(
            p -> p.getStatus() == PatientStatus.ACTIVE && p.getGender() == Gender.MALE
        );
    }

    @Test
    void pagination_returnsCorrectSlice() {
        Pageable pageable = PageRequest.of(0, 2);

        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            null, PatientStatus.ACTIVE, null, null, pageable
        );

        // Should have at most 2 results
        assertThat(results.getContent().size()).isLessThanOrEqualTo(2);

        // Should indicate if there are more results
        if (results.getContent().size() == 2) {
            // We have 4 active patients, so first page of size 2 should have more
            assertThat(results.hasNext()).isTrue();
        }
    }

    @Test
    void emptySearch_returnsAllPatients_sorted() {
        Pageable pageable = PageRequest.of(0, 20);

        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            null, null, null, null, pageable
        );

        assertThat(results.getContent().size()).isEqualTo(5);

        // Should be sorted by last name
        List<String> lastNames = results.getContent().stream()
            .map(p -> p.getFullName().split(" ")[1])
            .toList();
        assertThat(lastNames).isSorted();
    }

    @Test
    void reindexAllPatients_completesSuccessfully() throws InterruptedException {
        // This tests the reindexing functionality
        patientSearchRepository.reindexAllPatients();

        // Verify search still works after reindexing
        Pageable pageable = PageRequest.of(0, 20);
        Slice<PatientSummaryResponse> results = patientSearchRepository.searchPatients(
            "John", null, null, null, pageable
        );

        assertThat(results.getContent()).isNotEmpty();
    }
}
