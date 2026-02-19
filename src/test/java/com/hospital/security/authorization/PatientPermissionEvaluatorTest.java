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
 * Tests verify placeholder authorization logic for different roles:
 * - ADMIN: Full access to all patients
 * - DOCTOR: Placeholder allows all access (Phase 1: patient assignment checks)
 * - NURSE: Read-only access (Phase 1: care team checks)
 * - RECEPTIONIST: Placeholder allows all access (Phase 1: department checks)
 * - Unauthenticated: No access
 */
class PatientPermissionEvaluatorTest {

    private PatientPermissionEvaluator permissionEvaluator;

    @BeforeEach
    void setUp() {
        permissionEvaluator = new PatientPermissionEvaluator();
    }

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

    @Test
    void doctor_canReadPatients() {
        Authentication auth = createAuthentication("doctor1", "ROLE_DOCTOR");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void doctor_canWritePatients_placeholder() {
        // Placeholder logic allows write - Phase 1 will restrict based on assignments
        Authentication auth = createAuthentication("doctor1", "ROLE_DOCTOR");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    @Test
    void nurse_canReadPatients() {
        Authentication auth = createAuthentication("nurse1", "ROLE_NURSE");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void nurse_cannotWritePatients() {
        // Placeholder logic: nurses have read-only access
        Authentication auth = createAuthentication("nurse1", "ROLE_NURSE");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

    @Test
    void nurse_cannotDeletePatients() {
        Authentication auth = createAuthentication("nurse1", "ROLE_NURSE");
        assertFalse(permissionEvaluator.hasPermission(auth, 123L, "delete"));
    }

    @Test
    void receptionist_canReadPatients_placeholder() {
        // Placeholder logic allows all - Phase 1 will restrict to department
        Authentication auth = createAuthentication("receptionist1", "ROLE_RECEPTIONIST");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "read"));
    }

    @Test
    void receptionist_canWritePatients_placeholder() {
        // Placeholder logic allows write - Phase 1 will restrict based on department
        Authentication auth = createAuthentication("receptionist1", "ROLE_RECEPTIONIST");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "write"));
    }

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
    void hasPermission_supportsStringPatientId() {
        // Verify that String patient IDs are supported
        Authentication auth = createAuthentication("admin", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, "patient-123", "read"));
    }

    @Test
    void multipleRoles_adminTakesPrecedence() {
        // User with multiple roles - ADMIN permission should grant full access
        Authentication auth = createAuthentication("user1", "ROLE_NURSE", "ROLE_ADMIN");
        assertTrue(permissionEvaluator.hasPermission(auth, 123L, "write"));
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
