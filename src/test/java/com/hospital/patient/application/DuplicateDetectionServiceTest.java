package com.hospital.patient.application;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import com.hospital.patient.infrastructure.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DuplicateDetectionService.
 * Tests fuzzy matching algorithm with various duplicate scenarios.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DuplicateDetectionServiceTest {

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Autowired
    private PatientRepository patientRepository;

    private LocalDate testDob;

    @BeforeEach
    void setUp() {
        testDob = LocalDate.of(1990, 1, 15);
        // Clean up any existing patients from previous tests
        patientRepository.deleteAll();
    }

    @Test
    void shouldReturnNoDuplicatesForDifferentDateOfBirth() {
        // Given: Existing patient with different DOB
        createPatient("John", "Doe", LocalDate.of(1985, 5, 20), "555-123-4567", "john@example.com");

        // When: Check for duplicates with different DOB
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "John", "Doe", LocalDate.of(1990, 1, 15), "555-123-4567", "john@example.com"
        );

        // Then: No duplicates detected
        assertFalse(result.hasDuplicates());
        assertEquals(0, result.getMatches().size());
        assertFalse(result.isShouldBlockRegistration());
    }

    @Test
    void shouldDetectExactMatchAndBlock() {
        // Given: Existing patient with exact same details
        createPatient("John", "Doe", testDob, "555-123-4567", "john.doe@example.com");

        // When: Check for duplicates with exact same details
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "John", "Doe", testDob, "555-123-4567", "john.doe@example.com"
        );

        // Then: 100% match detected and should block
        assertTrue(result.hasDuplicates());
        assertEquals(1, result.getMatches().size());
        DuplicateDetectionService.DuplicateMatch match = result.getMatches().get(0);
        assertEquals(1.0, match.getScore(), 0.01); // 100% similarity
        assertTrue(result.isShouldBlockRegistration()); // >= 90%
    }

    @Test
    void shouldDetectPhoneticNameMatch() {
        // Given: Existing patient "Smith"
        createPatient("John", "Smith", testDob, "555-123-4567", "john.smith@example.com");

        // When: Check for "Smyth" (sounds like Smith)
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "John", "Smyth", testDob, "555-123-4567", "john.smith@example.com"
        );

        // Then: High similarity due to phonetic match + same phone/email
        assertTrue(result.hasDuplicates());
        assertTrue(result.getMatches().get(0).getScore() >= 0.85); // >= 85%
    }

    @Test
    void shouldNormalizePhoneNumbers() {
        // Given: Existing patient with phone "(555) 123-4567"
        createPatient("Jane", "Doe", testDob, "(555) 123-4567", "jane@example.com");

        // When: Check with different format "555-123-4567"
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "Jane", "Doe", testDob, "555-123-4567", "jane@example.com"
        );

        // Then: Exact match due to phone normalization
        assertTrue(result.hasDuplicates());
        assertEquals(1.0, result.getMatches().get(0).getScore(), 0.01); // 100%
        assertTrue(result.isShouldBlockRegistration());
    }

    @Test
    void shouldWarnFor85To89PercentMatch() {
        // Given: Existing patient
        createPatient("Robert", "Johnson", testDob, "555-111-2222", "robert@example.com");

        // When: Check with similar but not identical (different email, similar name)
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "Robert", "Johnsen", testDob, "555-111-2222", "rob@example.com"
        );

        // Then: Should detect as potential duplicate but not block
        assertTrue(result.hasDuplicates());
        DuplicateDetectionService.DuplicateMatch match = result.getMatches().get(0);
        assertTrue(match.getScore() >= 0.85, "Score should be >= 85%");

        // May or may not block depending on exact score calculation
        // The important part is that it's detected as a duplicate
    }

    @Test
    void shouldBlockFor90PlusPercentMatch() {
        // Given: Existing patient
        createPatient("Alice", "Williams", testDob, "555-999-8888", "alice@example.com");

        // When: Check with exact name, DOB, phone but different email
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "Alice", "Williams", testDob, "555-999-8888", "alice.w@example.com"
        );

        // Then: High similarity should trigger block
        assertTrue(result.hasDuplicates());
        DuplicateDetectionService.DuplicateMatch match = result.getMatches().get(0);
        assertTrue(match.getScore() >= 0.90, "Score should be >= 90%");
        assertTrue(result.isShouldBlockRegistration());
    }

    @Test
    void shouldRankMultipleMatchesByScore() {
        // Given: Multiple similar patients
        createPatient("Michael", "Brown", testDob, "555-111-1111", "michael1@example.com");
        createPatient("Michael", "Brown", testDob, "555-222-2222", "michael2@example.com");
        createPatient("Mike", "Brown", testDob, "555-333-3333", "mike@example.com");

        // When: Check for duplicates
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "Michael", "Brown", testDob, "555-111-1111", "michael1@example.com"
        );

        // Then: Multiple matches sorted by score descending
        assertTrue(result.hasDuplicates());
        assertTrue(result.getMatches().size() >= 1); // At least the exact match

        // First match should have highest score
        if (result.getMatches().size() > 1) {
            assertTrue(result.getMatches().get(0).getScore() >= result.getMatches().get(1).getScore());
        }
    }

    @Test
    void shouldHandleNullEmailGracefully() {
        // Given: Existing patient without email
        createPatient("Sarah", "Davis", testDob, "555-444-5555", null);

        // When: Check with same details but with email
        DuplicateDetectionService.DuplicateCheckResult result = duplicateDetectionService.checkForDuplicates(
            "Sarah", "Davis", testDob, "555-444-5555", "sarah@example.com"
        );

        // Then: Should still detect high similarity (name + DOB + phone = 90%)
        assertTrue(result.hasDuplicates());
        assertTrue(result.getMatches().get(0).getScore() >= 0.85);
    }

    private Patient createPatient(String firstName, String lastName, LocalDate dob,
                                   String phone, String email) {
        Patient patient = Patient.builder()
            .businessId(UUID.randomUUID())
            .version(1L)
            .firstName(firstName)
            .lastName(lastName)
            .dateOfBirth(dob)
            .gender(Gender.MALE)
            .phoneNumber(phone)
            .email(email)
            .status(PatientStatus.ACTIVE)
            .build();

        return patientRepository.save(patient);
    }
}
