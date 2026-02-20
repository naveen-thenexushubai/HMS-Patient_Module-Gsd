package com.hospital.patient.application;

import com.hospital.events.PatientUpdatedEvent;
import com.hospital.patient.api.dto.*;
import com.hospital.patient.domain.*;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PatientService {
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private EmergencyContactRepository emergencyContactRepository;
    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired
    private DuplicateDetectionService duplicateDetectionService;
    @Autowired
    private PatientSearchRepository patientSearchRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public DuplicateDetectionService.DuplicateCheckResult checkForDuplicates(RegisterPatientRequest request) {
        return duplicateDetectionService.checkForDuplicates(
            request.getFirstName(), request.getLastName(), request.getDateOfBirth(),
            request.getPhoneNumber(), request.getEmail()
        );
    }

    /**
     * Get the current authenticated username from Spring Security context.
     * Falls back to "system" if no authentication is present.
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "system";
    }

    public PatientDetailResponse registerPatient(RegisterPatientRequest request) {
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
            .photoIdVerified(request.getPhotoIdVerified())
            .status(PatientStatus.ACTIVE)
            .build();

        Patient savedPatient = patientRepository.save(patient);

        String currentUser = getCurrentUsername();

        List<EmergencyContact> savedContacts = new ArrayList<>();
        if (request.getEmergencyContacts() != null) {
            for (EmergencyContactDto ecDto : request.getEmergencyContacts()) {
                EmergencyContact contact = EmergencyContact.builder()
                    .patientBusinessId(savedPatient.getBusinessId())
                    .name(ecDto.getName())
                    .phoneNumber(ecDto.getPhoneNumber())
                    .relationship(ecDto.getRelationship())
                    .isPrimary(ecDto.getIsPrimary() != null ? ecDto.getIsPrimary() : false)
                    .createdBy(currentUser)
                    .build();
                savedContacts.add(emergencyContactRepository.save(contact));
            }
        }

        MedicalHistory savedHistory = null;
        if (request.getMedicalHistory() != null) {
            MedicalHistory history = MedicalHistory.builder()
                .patientBusinessId(savedPatient.getBusinessId())
                .bloodGroup(request.getMedicalHistory().getBloodGroup())
                .allergies(request.getMedicalHistory().getAllergies())
                .chronicConditions(request.getMedicalHistory().getChronicConditions())
                .createdBy(currentUser)
                .build();
            savedHistory = medicalHistoryRepository.save(history);
        }

        return toDetailResponse(savedPatient, savedContacts, savedHistory);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<PatientSummaryResponse> searchPatients(
        String query,
        PatientStatus status,
        Gender gender,
        String bloodGroup,
        org.springframework.data.domain.Pageable pageable
    ) {
        return patientSearchRepository.searchPatients(query, status, gender, bloodGroup, pageable);
    }

    @Transactional(readOnly = true)
    public PatientDetailResponse getPatientByBusinessId(UUID businessId) {
        Patient patient = patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // Use ordered query to return primary contact first; use findLatest for medical history
        List<EmergencyContact> contacts = emergencyContactRepository
            .findByPatientBusinessIdOrderByIsPrimaryDesc(businessId);
        MedicalHistory history = medicalHistoryRepository
            .findLatestByPatientBusinessId(businessId).orElse(null);

        return toDetailResponse(patient, contacts, history);
    }

    /**
     * Update patient demographics by inserting a new version row.
     * CRITICAL INVARIANT: PostgreSQL trigger prevents UPDATE on patients table.
     * Every "update" = INSERT new row with version = previousMax + 1.
     *
     * photoIdVerified and status are COPIED from the current version — not from the request.
     * businessId is PRESERVED from the current version — never changes.
     *
     * @throws PatientNotFoundException if no patient exists with this businessId
     */
    public PatientDetailResponse updatePatient(UUID businessId, UpdatePatientRequest request) {
        // 1. Load current latest version
        Patient current = patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // 2. Compute next version number
        Long nextVersion = patientRepository.findMaxVersionByBusinessId(businessId)
            .orElse(0L) + 1;

        // 3. Build new version — apply request changes, PRESERVE photoIdVerified and status
        Patient newVersion = Patient.builder()
            // patientId: auto-generated by PatientIdGenerator via @GeneratedValue
            .businessId(current.getBusinessId())        // PRESERVED — never changes
            .version(nextVersion)                        // INCREMENTED
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
            .photoIdVerified(current.getPhotoIdVerified()) // PRESERVED — not in update form
            .status(current.getStatus())                   // PRESERVED — use /status endpoint to change
            // createdAt, createdBy: set by AuditingEntityListener (@CreatedDate, @CreatedBy)
            .build();

        Patient saved = patientRepository.save(newVersion);

        // 4. Publish domain event after transaction commits (listener uses AFTER_COMMIT)
        eventPublisher.publishEvent(new PatientUpdatedEvent(
            this,
            saved.getBusinessId(),
            saved.getVersion(),
            getCurrentUsername(),
            List.of("demographics")
        ));

        return toDetailResponse(saved);
    }

    /**
     * Change patient status (ACTIVE/INACTIVE) by inserting a new version row.
     * Same event-sourced insert pattern as updatePatient().
     * If the patient is already in the requested status, returns current state (idempotent).
     *
     * Only ADMIN role can call this (enforced at controller via @PreAuthorize with hasRole('ADMIN')).
     */
    public PatientDetailResponse changePatientStatus(UUID businessId, PatientStatus newStatus) {
        Patient current = patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // Idempotent: if already in target status, return current state without inserting new version
        if (current.getStatus() == newStatus) {
            return toDetailResponse(current);
        }

        Long nextVersion = patientRepository.findMaxVersionByBusinessId(businessId)
            .orElse(0L) + 1;

        // Copy all fields from current version; only status changes
        Patient statusVersion = Patient.builder()
            .businessId(current.getBusinessId())
            .version(nextVersion)
            .firstName(current.getFirstName())
            .lastName(current.getLastName())
            .dateOfBirth(current.getDateOfBirth())
            .gender(current.getGender())
            .phoneNumber(current.getPhoneNumber())
            .email(current.getEmail())
            .addressLine1(current.getAddressLine1())
            .addressLine2(current.getAddressLine2())
            .city(current.getCity())
            .state(current.getState())
            .zipCode(current.getZipCode())
            .photoIdVerified(current.getPhotoIdVerified())
            .status(newStatus)   // ONLY this field changes from current
            .build();

        Patient saved = patientRepository.save(statusVersion);

        eventPublisher.publishEvent(new PatientUpdatedEvent(
            this,
            saved.getBusinessId(),
            saved.getVersion(),
            getCurrentUsername(),
            List.of("status")
        ));

        return toDetailResponse(saved);
    }

    // Private overload for update/status responses (contacts/history not changed by these ops)
    private PatientDetailResponse toDetailResponse(Patient latestVersion) {
        List<EmergencyContact> contacts = emergencyContactRepository
            .findByPatientBusinessIdOrderByIsPrimaryDesc(latestVersion.getBusinessId());
        MedicalHistory history = medicalHistoryRepository
            .findLatestByPatientBusinessId(latestVersion.getBusinessId()).orElse(null);
        return toDetailResponse(latestVersion, contacts, history);
    }

    private PatientDetailResponse toDetailResponse(Patient patient, List<EmergencyContact> contacts, MedicalHistory history) {
        // Fetch version-1 row to get original registeredAt and registeredBy
        // For version-1 patients this will be the same as patient; for updated patients it will differ
        Patient originalVersion = patientRepository.findFirstVersionByBusinessId(patient.getBusinessId())
            .orElse(patient); // fallback: use latest if version-1 not found (should never happen)

        List<EmergencyContactDto> contactDtos = null;
        if (contacts != null && !contacts.isEmpty()) {
            contactDtos = contacts.stream()
                .map(ec -> EmergencyContactDto.builder()
                    .id(ec.getId())
                    .name(ec.getName())
                    .phoneNumber(ec.getPhoneNumber())
                    .relationship(ec.getRelationship())
                    .isPrimary(ec.getIsPrimary())
                    .build())
                .collect(Collectors.toList());
        }

        MedicalHistoryDto historyDto = null;
        if (history != null) {
            historyDto = MedicalHistoryDto.builder()
                .bloodGroup(history.getBloodGroup())
                .allergies(history.getAllergies())
                .chronicConditions(history.getChronicConditions())
                .build();
        }

        return PatientDetailResponse.builder()
            .patientId(patient.getPatientId())
            .businessId(patient.getBusinessId())
            .firstName(patient.getFirstName())
            .lastName(patient.getLastName())
            .dateOfBirth(patient.getDateOfBirth())
            .age(patient.getAge())
            .gender(patient.getGender())
            .phoneNumber(patient.getPhoneNumber())
            .email(patient.getEmail())
            .addressLine1(patient.getAddressLine1())
            .addressLine2(patient.getAddressLine2())
            .city(patient.getCity())
            .state(patient.getState())
            .zipCode(patient.getZipCode())
            .status(patient.getStatus())
            .emergencyContacts(contactDtos)
            .medicalHistory(historyDto)
            .registeredAt(originalVersion.getCreatedAt())    // FROM VERSION 1
            .registeredBy(originalVersion.getCreatedBy())    // FROM VERSION 1
            .lastModifiedAt(patient.getCreatedAt())          // FROM LATEST VERSION
            .lastModifiedBy(patient.getCreatedBy())          // FROM LATEST VERSION
            .version(patient.getVersion())
            .build();
    }
}
