package com.hospital.portal.application;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.application.AppointmentService;
import com.hospital.patient.application.InsuranceService;
import com.hospital.patient.application.PatientService;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.portal.api.dto.*;
import com.hospital.portal.domain.PatientCredential;
import com.hospital.portal.infrastructure.PatientCredentialRepository;
import com.hospital.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PatientPortalService {

    @Autowired
    private PatientCredentialRepository credentialRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PatientService patientService;

    @Autowired
    private InsuranceService insuranceService;

    @Autowired
    private AppointmentService appointmentService;

    /**
     * Register a portal account for a patient.
     * Verifies patientId+DOB match, then creates BCrypt-hashed PIN credential.
     *
     * @throws PatientNotFoundException if patientId not found
     * @throws IllegalArgumentException if DOB does not match or email/patient already registered
     */
    public void registerPortalAccount(PortalRegisterRequest request) {
        // 1. Find patient by patientId (human-readable ID like P202601001)
        Patient patient = patientRepository.findByPatientId(request.getPatientId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Patient not found with ID: " + request.getPatientId()));

        // 2. Verify date of birth matches
        if (!patient.getDateOfBirth().equals(request.getDateOfBirth())) {
            throw new IllegalArgumentException("Date of birth does not match our records");
        }

        // 3. Check no existing portal account for this patient
        if (credentialRepository.existsByPatientBusinessId(patient.getBusinessId())) {
            throw new IllegalArgumentException("A portal account already exists for this patient");
        }

        // 4. Check email not already taken
        if (credentialRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new IllegalArgumentException("This email address is already registered");
        }

        // 5. Create credential with BCrypt-hashed PIN
        PatientCredential credential = PatientCredential.builder()
            .patientBusinessId(patient.getBusinessId())
            .email(request.getEmail().toLowerCase().trim())
            .pinHash(passwordEncoder.encode(request.getPin()))
            .isActive(true)
            .build();

        credentialRepository.save(credential);
    }

    /**
     * Authenticate a patient and return a portal JWT token.
     *
     * @throws IllegalArgumentException if credentials are invalid or account inactive
     */
    public PortalLoginResponse loginPortal(PortalLoginRequest request) {
        PatientCredential credential = credentialRepository
            .findByEmail(request.getEmail().toLowerCase().trim())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or PIN"));

        if (!Boolean.TRUE.equals(credential.getIsActive())) {
            throw new IllegalArgumentException("Portal account is inactive. Contact reception.");
        }

        if (!passwordEncoder.matches(request.getPin(), credential.getPinHash())) {
            throw new IllegalArgumentException("Invalid email or PIN");
        }

        // Update last login timestamp
        credential.setLastLoginAt(Instant.now());
        credentialRepository.save(credential);

        // Generate patient JWT with ROLE_PATIENT + patientBusinessId claim
        String token = jwtTokenProvider.generatePatientToken(
            credential.getPatientBusinessId().toString(),
            credential.getEmail()
        );

        return PortalLoginResponse.builder()
            .token(token)
            .patientBusinessId(credential.getPatientBusinessId())
            .role("PATIENT")
            .build();
    }

    /**
     * Get the patient's own profile. Delegates to PatientService.
     */
    @Transactional(readOnly = true)
    public PatientDetailResponse getOwnProfile(UUID patientBusinessId) {
        return patientService.getPatientByBusinessId(patientBusinessId);
    }

    /**
     * Update the patient's own contact information.
     * Uses the event-sourced update pattern (new version insert).
     */
    public PatientDetailResponse updateOwnContact(UUID patientBusinessId, UpdateContactRequest request) {
        Patient current = patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        // Build UpdatePatientRequest from the contact update — preserve all other fields from current
        UpdatePatientRequest updateRequest = UpdatePatientRequest.builder()
            .firstName(current.getFirstName())
            .lastName(current.getLastName())
            .dateOfBirth(current.getDateOfBirth())
            .gender(current.getGender())
            .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber() : current.getPhoneNumber())
            .email(request.getEmail() != null ? request.getEmail() : current.getEmail())
            .addressLine1(request.getAddressLine1() != null ? request.getAddressLine1() : current.getAddressLine1())
            .addressLine2(request.getAddressLine2() != null ? request.getAddressLine2() : current.getAddressLine2())
            .city(request.getCity() != null ? request.getCity() : current.getCity())
            .state(request.getState() != null ? request.getState() : current.getState())
            .zipCode(request.getZipCode() != null ? request.getZipCode() : current.getZipCode())
            .isRegistrationComplete(current.getIsRegistrationComplete())
            .build();

        return patientService.updatePatient(patientBusinessId, updateRequest);
    }

    /**
     * Update the patient's own insurance.
     */
    public InsuranceDto updateOwnInsurance(UUID patientBusinessId, InsuranceDto request) {
        // Try update first; if no active insurance, create new
        try {
            return insuranceService.updateInsurance(patientBusinessId, request);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return insuranceService.createInsurance(patientBusinessId, request);
        }
    }

    /**
     * Get upcoming appointments for the patient.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSummaryDto> getOwnAppointments(UUID patientBusinessId) {
        return appointmentService.getUpcomingAppointments(patientBusinessId);
    }

    /**
     * Complete pre-registration by filling in missing fields.
     * Sets isRegistrationComplete = true.
     */
    public PatientDetailResponse completePreRegistration(UUID patientBusinessId, UpdatePatientRequest request) {
        // Force isRegistrationComplete = true when completing via portal
        request.setIsRegistrationComplete(true);
        return patientService.updatePatient(patientBusinessId, request);
    }
}
