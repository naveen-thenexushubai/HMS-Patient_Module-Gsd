package com.hospital.patient.application;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.domain.*;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    private PatientDetailResponse toDetailResponse(Patient patient, List<EmergencyContact> contacts, MedicalHistory history) {
        List<EmergencyContactDto> contactDtos = null;
        if (contacts != null && !contacts.isEmpty()) {
            contactDtos = contacts.stream()
                .map(ec -> EmergencyContactDto.builder()
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
            .registeredAt(patient.getCreatedAt())
            .registeredBy(patient.getCreatedBy())
            .lastModifiedAt(patient.getCreatedAt())
            .lastModifiedBy(patient.getCreatedBy())
            .version(patient.getVersion())
            .build();
    }
}
