package com.hospital.patient.application;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.domain.*;
import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for quick patient registration with minimal required fields.
 * Resulting patient has isRegistrationComplete=false and photoIdVerified=false.
 *
 * Follows the same save pattern as PatientService.registerPatient() but:
 * - Accepts QuickRegisterRequest (no @AssertTrue photoIdVerified)
 * - Sets photoIdVerified=false explicitly
 * - Sets isRegistrationComplete=false to flag the record for completion
 * - STILL runs DuplicateDetectionService (duplicate detection is not optional)
 *
 * When receptionist completes the record later, PatientService.updatePatient()
 * with isRegistrationComplete=true in UpdatePatientRequest inserts a new version row.
 */
@Service
@Transactional
public class QuickRegistrationService {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Autowired
    private PatientService patientService;  // For toDetailResponse / InsuranceService access

    /**
     * Run duplicate detection on a QuickRegisterRequest.
     * Called by the controller before proceeding with registration.
     */
    public DuplicateDetectionService.DuplicateCheckResult checkForDuplicates(QuickRegisterRequest request) {
        return duplicateDetectionService.checkForDuplicates(
            request.getFirstName(), request.getLastName(), request.getDateOfBirth(),
            request.getPhoneNumber(), request.getEmail()
        );
    }

    /**
     * Register a patient with minimal fields (walk-in triage).
     * Sets isRegistrationComplete=false and photoIdVerified=false.
     *
     * @param request minimal registration fields
     * @return PatientDetailResponse with isRegistrationComplete=false
     */
    public PatientDetailResponse quickRegisterPatient(QuickRegisterRequest request) {
        Patient patient = Patient.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .dateOfBirth(request.getDateOfBirth())
            .gender(request.getGender())
            .phoneNumber(request.getPhoneNumber())
            .email(request.getEmail())
            .addressLine1(request.getAddressLine1())
            .addressLine2(request.getAddressLine2())
            .city(request.getCity())
            .state(request.getState())
            .zipCode(request.getZipCode())
            .photoIdVerified(false)            // Not verified — walk-in patient
            .isRegistrationComplete(false)     // Pending full data entry
            .status(PatientStatus.ACTIVE)
            .build();

        patientRepository.save(patient);

        // Reload via patientService.getPatientByBusinessId to use the same
        // response-building logic (includes insurance, contacts, version-1 registeredAt)
        return patientService.getPatientByBusinessId(patient.getBusinessId());
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "system";
    }
}
