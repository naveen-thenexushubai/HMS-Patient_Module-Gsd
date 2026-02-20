package com.hospital.security.authorization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PatientPermissionEvaluator.
 *
 * Tests verify actual role-based authorization logic:
 * - ADMIN: Full access to all patients
 * - RECEPTIONIST: Full access (registration and administrative tasks)
 * - DOCTOR: Read-only access (Phase 2: will add patient_assignments check)
 * - NURSE: Read-only access (Phase 2: will add care_team check)
 * - Unauthenticated: No access
 *
 * Note: PatientRepository is not injected in unit tests (uses Spring context).
 * See PatientPermissionEvaluatorIntegrationTest for full integration tests.
 */
class PatientPermissionEvaluatorTest {

    private PatientPermissionEvaluator permissionEvaluator;

    @BeforeEach
    void setUp() {
        permissionEvaluator = new PatientPermissionEvaluator();
    }

    // --- ADMIN tests ---

    @Test
    void admin_canReadPatient() {
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void admin_canWritePatient() {
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    @Test
    void admin_canDeletePatient() {
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "delete"));
    }

    // --- RECEPTIONIST tests ---

    @Test
    void receptionist_canReadPatients() {
        Authentication auth = createAuthentication("receptionist1", "ROLE_RECEPTIONIST");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void receptionist_canWritePatients() {
        Authentication auth = createAuthentication("receptionist1", "ROLE_RECEPTIONIST");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    @Test
    void receptionist_canDeletePatients() {
        Authentication auth = createAuthentication("receptionist1", "ROLE_RECEPTIONIST");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "delete"));
    }

    // --- DOCTOR tests ---

    @Test
    void doctor_canReadPatients() {
        Authentication auth = createAuthentication("doctor1", "ROLE_DOCTOR");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void doctor_cannotWritePatients() {
        // Actual logic: doctors have read-only access (Phase 2: patient_assignments check)
        Authentication auth = createAuthentication("doctor1", "ROLE_DOCTOR");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    @Test
    void doctor_cannotDeletePatients() {
        Authentication auth = createAuthentication("doctor1", "ROLE_DOCTOR");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "delete"));
    }

    // --- NURSE tests ---

    @Test
    void nurse_canReadPatients() {
        Authentication auth = createAuthentication("nurse1", "ROLE_NURSE");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void nurse_cannotWritePatients() {
        // Actual logic: nurses have read-only access (Phase 2: care_team check)
        Authentication auth = createAuthentication("nurse1", "ROLE_NURSE");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    @Test
    void nurse_cannotDeletePatients() {
        Authentication auth = createAuthentication("nurse1", "ROLE_NURSE");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "delete"));
    }

    // --- Edge cases ---

    @Test
    void unauthenticated_cannotAccessPatients() {
        assertFalse(permissionEvaluator.hasPermission(null, 123L, "read"));
    }

    @Test
    void userWithoutRole_cannotAccessPatients() {
        Authentication auth = createAuthentication("user1");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void hasPermission_withSerializable_delegatesToMainMethod() {
        // Test the overloaded method that takes targetId and targetType
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "Patient", "read"));
    }

    @Test
    void hasPermission_unknownTargetType_deniesAccess() {
        // Non-Patient target types should be denied
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "Department", "read"));
    }

    @Test
    void multipleRoles_adminTakesPrecedence() {
        // User with multiple roles - ADMIN permission should grant full access
        Authentication auth = createAuthentication("user1", "ROLE_NURSE", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    // --- canEdit helper tests ---

    @Test
    void canEdit_returnsTrueForAdmin() {
        assertTrue(permissionEvaluator.canEdit("admin", "ROLE_ADMIN"));
    }

    @Test
    void canEdit_returnsTrueForReceptionist() {
        assertTrue(permissionEvaluator.canEdit("receptionist", "ROLE_RECEPTIONIST"));
    }

    @Test
    void canEdit_returnsFalseForDoctor() {
        assertFalse(permissionEvaluator.canEdit("doctor", "ROLE_DOCTOR"));
    }

    @Test
    void canEdit_returnsFalseForNurse() {
        assertFalse(permissionEvaluator.canEdit("nurse", "ROLE_NURSE"));
    }

    /**
     * Helper method to create Authentication with specified username and roles.
     */
    private Authentication createAuthentication(String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }
}
