package com.hospital.security.authorization;

import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import com.hospital.security.jwt.PatientAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Custom PermissionEvaluator for object-level authorization of patient data access.
 *
 * Role capabilities matrix:
 * - ADMIN: Full access (read, write, delete) to all patients
 * - RECEPTIONIST: Full access (read, write, delete) for registration and administrative tasks
 * - DOCTOR: Read-only access to all patients
 *   Phase 2: Will add patient_assignments table check to restrict to assigned patients
 * - NURSE: Read-only access to all patients
 *   Phase 2: Will add care_team table check to restrict to care team patients
 *
 * Permission evaluation flow:
 *   @PreAuthorize("hasPermission(#businessId, 'Patient', 'read')")
 *   -> MethodSecurityExpressionHandler
 *   -> PatientPermissionEvaluator.hasPermission()
 *
 * Phase 2 integration points:
 * - patient_assignments table: Link doctors to specific patients
 * - care_team table: Link nurses to patients in their care team
 * - department_assignments: Restrict receptionist access by department
 */
@Component
public class PatientPermissionEvaluator implements PermissionEvaluator {

    /**
     * PatientRepository injected and ready for Phase 2 patient_assignments queries.
     * Not used in Phase 1 role checks but infrastructure is in place.
     */
    @Autowired
    private PatientRepository patientRepository;

    /**
     * Evaluates permission for a given user to access a patient object.
     * Used when targetDomainObject is an actual domain object instance or a patient ID.
     *
     * This evaluator is Patient-scoped and defaults target type to "Patient".
     * Supports both:
     * - Patient domain object instance
     * - Patient ID (Long, UUID, String) used as a reference
     *
     * @param authentication The authenticated user
     * @param targetDomainObject The patient domain object or patient ID
     * @param permission The permission type ("read", "write", "delete")
     * @return true if access is granted, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // This evaluator is Patient-scoped — always use "Patient" as the target type.
        // The second overload handles the actual type check; here we delegate directly.
        return hasPermission(authentication, (Serializable) targetDomainObject, "Patient", permission.toString());
    }

    /**
     * Evaluates permission using target ID and type string.
     * Primary method used by @PreAuthorize expressions.
     *
     * @param authentication The authenticated user
     * @param targetId The patient business ID
     * @param targetType The target type string (must be "Patient")
     * @param permission The permission type ("read", "write", "delete")
     * @return true if access is granted, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (!"Patient".equals(targetType)) {
            return false;
        }

        String permissionString = permission.toString();

        // ADMIN has full access to all patients
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }

        // RECEPTIONIST has full access for registration and administrative tasks
        // Phase 2: Add department_assignments check for fine-grained control
        if (hasRole(authentication, "RECEPTIONIST")) {
            return true;
        }

        // DOCTOR has read-only access to all patients
        // Phase 2: Add patient_assignments table check:
        //   return "read".equals(permissionString) &&
        //       patientRepository.isAssignedToDoctor(targetId, authentication.getName());
        if (hasRole(authentication, "DOCTOR")) {
            return "read".equals(permissionString);
        }

        // NURSE has read-only access to all patients
        // Phase 2: Add care_team table check:
        //   return "read".equals(permissionString) &&
        //       patientRepository.isInNurseCareTeam(targetId, authentication.getName());
        if (hasRole(authentication, "NURSE")) {
            return "read".equals(permissionString);
        }

        // PATIENT role — read-only access to their own record only
        if (hasRole(authentication, "PATIENT")) {
            if (!"read".equals(permissionString)) {
                return false;
            }
            if (authentication.getDetails() instanceof PatientAuthenticationDetails patientDetails) {
                String tokenPatientId = patientDetails.getPatientBusinessId();
                return tokenPatientId != null && tokenPatientId.equals(targetId.toString());
            }
            return false;
        }

        // Default deny — unknown roles have no access
        return false;
    }

    /**
     * Check if user can edit patient.
     * Used by frontend to show/hide Edit button based on role.
     *
     * @param username The username (for future Phase 2 user-specific checks)
     * @param role The user's role (with ROLE_ prefix)
     * @return true if the user can edit patient records
     */
    public boolean canEdit(String username, String role) {
        return "ROLE_ADMIN".equals(role) || "ROLE_RECEPTIONIST".equals(role);
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
