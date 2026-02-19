package com.hospital.security.authorization;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Custom PermissionEvaluator for object-level authorization of patient data access.
 *
 * PLACEHOLDER IMPLEMENTATION: This evaluator provides basic role-based authorization
 * logic that will be refined in Phase 1 when the patient data model is implemented.
 *
 * Phase 1 Refinements:
 * - Inject PatientRepository to query patient_assignments table
 * - Implement actual doctor-patient assignment checks
 * - Implement care_team table queries for nurse access
 * - Implement department-based access for receptionist role
 * - Add permission-level checks (read vs write vs delete)
 *
 * Current placeholder logic:
 * - ADMIN: Full access to all patients
 * - DOCTOR: Allowed all access (Phase 1: check patient_assignments)
 * - NURSE: Read-only access (Phase 1: check care_team)
 * - RECEPTIONIST: Allowed all access (Phase 1: check department assignments)
 */
@Component
public class PatientPermissionEvaluator implements PermissionEvaluator {

    /**
     * Evaluates permission for a given user to access a patient object.
     *
     * @param authentication The authenticated user
     * @param targetDomainObject The patient ID (can be Long or String)
     * @param permission The permission type ("read", "write", "delete")
     * @return true if access is granted, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Object patientId = targetDomainObject;  // Can be Long or String
        String permissionName = (String) permission;  // "read", "write", "delete"

        // ADMIN role can access all patients
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }

        // DOCTOR role - placeholder logic (Phase 1: check patient-doctor assignments)
        if (hasRole(authentication, "DOCTOR")) {
            // TODO Phase 1: Query patient_assignments table
            // return patientRepository.isAssignedToDoctor(patientId, username);
            return true;  // Placeholder: allow all for now
        }

        // NURSE role - placeholder logic (Phase 1: check care team assignments)
        if (hasRole(authentication, "NURSE")) {
            // TODO Phase 1: Query care_team table
            return "read".equals(permissionName);  // Placeholder: read-only
        }

        // RECEPTIONIST role - placeholder logic (Phase 1: department-based access)
        if (hasRole(authentication, "RECEPTIONIST")) {
            // TODO Phase 1: Query department assignments
            return true;  // Placeholder: allow all for now
        }

        return false;
    }

    /**
     * Evaluates permission using target ID and type string.
     * Delegates to the main hasPermission method.
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, targetId, permission);
    }

    /**
     * Helper method to check if the authenticated user has a specific role.
     *
     * @param authentication The authenticated user
     * @param role The role name (without ROLE_ prefix)
     * @return true if user has the role, false otherwise
     */
    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
