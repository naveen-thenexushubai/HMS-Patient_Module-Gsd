package com.hospital.patient.api;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.application.*;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * POST /api/v1/patients/quick — Quick patient registration with minimal fields.
 *
 * Accepts QuickRegisterRequest (firstName, lastName, dateOfBirth, gender, phoneNumber required).
 * Resulting patient has isRegistrationComplete=false and photoIdVerified=false.
 *
 * Duplicate detection: same logic as full registration.
 * - 85-89% similarity  -> 409 Conflict with duplicates list (overrideDuplicate=true proceeds)
 * - 90%+ similarity    -> 409 Conflict; overrideDuplicate=true returns 403 (admin approval required)
 *
 * Authorization: RECEPTIONIST or ADMIN only (same as full registration).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class QuickRegistrationController {

    @Autowired
    private QuickRegistrationService quickRegistrationService;

    @PostMapping("/quick")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "PATIENT")
    public ResponseEntity<?> quickRegisterPatient(
        @Valid @RequestBody QuickRegisterRequest request,
        @RequestParam(required = false, defaultValue = "false") boolean overrideDuplicate
    ) {
        DuplicateDetectionService.DuplicateCheckResult duplicateCheck =
            quickRegistrationService.checkForDuplicates(request);

        if (duplicateCheck.hasDuplicates() && !overrideDuplicate) {
            DuplicateWarningResponse warning = DuplicateWarningResponse.builder()
                .matches(duplicateCheck.getMatches().stream()
                    .map(match -> DuplicateMatchDto.builder()
                        .patientId(match.getPatient().getPatientId())
                        .fullName(match.getPatient().getFirstName() + " " + match.getPatient().getLastName())
                        .dateOfBirth(match.getPatient().getDateOfBirth())
                        .phoneNumber(match.getPatient().getPhoneNumber())
                        .email(match.getPatient().getEmail())
                        .similarityScore((int) (match.getScore() * 100))
                        .build())
                    .collect(Collectors.toList()))
                .requiresOverride(duplicateCheck.isShouldBlockRegistration())
                .message("Potential duplicate patient(s) detected. Review matches and confirm quick registration.")
                .build();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(warning);
        }

        if (duplicateCheck.isShouldBlockRegistration() && overrideDuplicate) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("High-confidence duplicate detected. Admin approval required.");
        }

        PatientDetailResponse patient = quickRegistrationService.quickRegisterPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
    }
}
