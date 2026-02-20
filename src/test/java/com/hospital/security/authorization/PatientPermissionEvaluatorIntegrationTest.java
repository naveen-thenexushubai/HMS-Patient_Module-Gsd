package com.hospital.security.authorization;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import com.hospital.patient.infrastructure.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PatientPermissionEvaluator with full Spring context.
 *
 * Verifies actual role-based authorization with PatientRepository injected.
 * Tests role capabilities matrix:
 * - ADMIN: Full access (read, write, delete)
 * - RECEPTIONIST: Full access (read, write, delete)
 * - DOCTOR: Read-only access
 * - NURSE: Read-only access
 * - Unauthenticated/Unknown: No access
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PatientPermissionEvaluatorIntegrationTest {

    @Autowired
    private PatientPermissionEvaluator permissionEvaluator;

    @Autowired
    private PatientRepository patientRepository;

    private Patient testPatient;
    private UUID testBusinessId;

    @BeforeEach
    void setUp() {
        patientRepository.deleteAll();

        testPatient = Patient.builder()
            .firstName("Test")
            .lastName("Patient")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .gender(Gender.MALE)
            .phoneNumber("555-123-4567")
            .status(PatientStatus.ACTIVE)
            .createdBy("test-setup")
            .build();
        testPatient = patientRepository.save(testPatient);
        testBusinessId = testPatient.getBusinessId();
    }

    // --- ADMIN tests ---

    @Test
    void admin_hasFullAccess() {
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");

        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "read"),
            "ADMIN should have read access");
        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "write"),
            "ADMIN should have write access");
        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "delete"),
            "ADMIN should have delete access");
    }

    // --- RECEPTIONIST tests ---

    @Test
    void receptionist_hasFullAccess() {
        Authentication auth = createAuthentication("receptionist", "ROLE_RECEPTIONIST");

        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "read"),
            "RECEPTIONIST should have read access");
        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "write"),
            "RECEPTIONIST should have write access");
        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "delete"),
            "RECEPTIONIST should have delete access");
    }

    // --- DOCTOR tests ---

    @Test
    void doctor_hasReadOnlyAccess() {
        Authentication auth = createAuthentication("doctor", "ROLE_DOCTOR");

        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "read"),
            "DOCTOR should have read access");
        assertFalse(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "write"),
            "DOCTOR should not have write access");
        assertFalse(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "delete"),
            "DOCTOR should not have delete access");
    }

    // --- NURSE tests ---

    @Test
    void nurse_hasReadOnlyAccess() {
        Authentication auth = createAuthentication("nurse", "ROLE_NURSE");

        assertTrue(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "read"),
            "NURSE should have read access");
        assertFalse(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "write"),
            "NURSE should not have write access");
        assertFalse(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "delete"),
            "NURSE should not have delete access");
    }

    // --- Edge case tests ---

    @Test
    void unauthenticated_hasNoAccess() {
        assertFalse(permissionEvaluator.hasPermission(null, testBusinessId, "Patient", "read"),
            "Unauthenticated user should have no access");
    }

    @Test
    void unknownRole_hasNoAccess() {
        Authentication auth = createAuthentication("guest", "ROLE_GUEST");

        assertFalse(permissionEvaluator.hasPermission(auth, testBusinessId, "Patient", "read"),
            "Unknown role should have no access");
    }

    @Test
    void canEdit_returnsTrueForAdminAndReceptionist() {
        assertTrue(permissionEvaluator.canEdit("admin", "ROLE_ADMIN"),
            "ADMIN should be able to edit");
        assertTrue(permissionEvaluator.canEdit("receptionist", "ROLE_RECEPTIONIST"),
            "RECEPTIONIST should be able to edit");
        assertFalse(permissionEvaluator.canEdit("doctor", "ROLE_DOCTOR"),
            "DOCTOR should not be able to edit");
        assertFalse(permissionEvaluator.canEdit("nurse", "ROLE_NURSE"),
            "NURSE should not be able to edit");
    }

    /**
     * Helper method to create Authentication with specified username and roles.
     */
    private Authentication createAuthentication(String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        return new UsernamePasswordAuthenticationToken(username, "password", authorities);
    }
}
