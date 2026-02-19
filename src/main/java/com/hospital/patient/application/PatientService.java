package com.hospital.patient.application;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.domain.*;
import com.hospital.patient.infrastructure.*;
import org.springframework.beans.factory.annotation.Autowired;
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

        List<EmergencyContact> savedContacts = new ArrayList<>();
        if (request.getEmergencyContacts() != null) {
            for (EmergencyContactDto ecDto : request.getEmergencyContacts()) {
                EmergencyContact contact = EmergencyContact.builder()
                    .patientBusinessId(savedPatient.getBusinessId())
                    .name(ecDto.getName())
                    .phoneNumber(ecDto.getPhoneNumber())
                    .relationship(ecDto.getRelationship())
                    .isPrimary(ecDto.getIsPrimary() != null ? ecDto.getIsPrimary() : false)
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
            .orElseThrow(() -> new RuntimeException("Patient not found: " + businessId));

        List<EmergencyContact> contacts = emergencyContactRepository.findByPatientBusinessId(businessId);
        MedicalHistory history = medicalHistoryRepository.findByPatientBusinessId(businessId)
            .stream().findFirst().orElse(null);

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
