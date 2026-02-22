package com.hospital.patient.application;

import com.hospital.patient.api.dto.MergePreviewResponse;
import com.hospital.patient.api.dto.PatientDetailResponse;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.*;
import com.hospital.security.audit.AuditLog;
import com.hospital.security.audit.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for the admin-only patient merge workflow.
 *
 * Merge semantics:
 * - TARGET WINS: target patient's demographics are kept unchanged
 * - ALL child entities from source are reassigned to target (union, no dedupe)
 * - Source patient gets a new INACTIVE version row (event-sourced INSERT pattern)
 * - Full merge audit log is written
 */
@Service
public class PatientMergeService {

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PatientService patientService;
    @Autowired
    private EmergencyContactRepository emergencyContactRepository;
    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired
    private InsuranceRepository insuranceRepository;
    @Autowired
    private PatientPhotoRepository patientPhotoRepository;
    @Autowired
    private PatientFamilyRepository patientFamilyRepository;
    @Autowired
    private PatientRelationshipRepository patientRelationshipRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Preview what a merge would do without executing it.
     * Returns both patients and counts of child entities that would move.
     */
    @Transactional(readOnly = true)
    public MergePreviewResponse previewMerge(UUID sourceId, UUID targetId) {
        validateMergeInputs(sourceId, targetId);

        PatientDetailResponse source = patientService.getPatientByBusinessId(sourceId);
        PatientDetailResponse target = patientService.getPatientByBusinessId(targetId);

        Map<String, Long> counts = buildChildCounts(sourceId);

        String desc = String.format(
            "Merging patient '%s %s' (source) into '%s %s' (target). " +
            "Source will be deactivated. All %d emergency contacts, %d medical histories, " +
            "%d insurance records, %d photos, %d family links, and %d relationships will move to target.",
            source.getFirstName(), source.getLastName(),
            target.getFirstName(), target.getLastName(),
            counts.get("emergencyContacts"), counts.get("medicalHistories"),
            counts.get("insurance"), counts.get("photos"),
            counts.get("families"), counts.get("relationships")
        );

        return MergePreviewResponse.builder()
            .sourcePatient(source)
            .targetPatient(target)
            .sourceChildEntityCounts(counts)
            .mergeDescription(desc)
            .build();
    }

    /**
     * Execute the merge: reassign all source child entities to target, deactivate source.
     * Returns the updated target patient after merge.
     */
    @Transactional
    public PatientDetailResponse mergePatients(UUID sourceId, UUID targetId) {
        validateMergeInputs(sourceId, targetId);

        // Load source and target latest versions
        Patient source = patientRepository.findLatestVersionByBusinessId(sourceId)
            .orElseThrow(() -> new PatientNotFoundException(sourceId.toString()));
        Patient target = patientRepository.findLatestVersionByBusinessId(targetId)
            .orElseThrow(() -> new PatientNotFoundException(targetId.toString()));

        // Verify source is ACTIVE
        if (source.getStatus() != PatientStatus.ACTIVE) {
            throw new IllegalStateException(
                "Source patient is already " + source.getStatus() + " — cannot merge an inactive patient");
        }

        // Record child entity counts before reassignment (for audit log)
        Map<String, Long> counts = buildChildCounts(sourceId);

        // Reassign all child entities from source to target
        emergencyContactRepository.reassignToPatient(sourceId, targetId);
        medicalHistoryRepository.reassignToPatient(sourceId, targetId);
        insuranceRepository.reassignToPatient(sourceId, targetId);
        patientPhotoRepository.reassignToPatient(sourceId, targetId);
        patientFamilyRepository.reassignToPatient(sourceId, targetId);
        patientRelationshipRepository.reassignOwnerToPatient(sourceId, targetId);
        patientRelationshipRepository.reassignRelatedToPatient(sourceId, targetId);

        // Deactivate source: insert new INACTIVE version (event-sourced pattern)
        Long nextVersion = patientRepository.findMaxVersionByBusinessId(sourceId).orElse(0L) + 1;
        Patient inactiveSource = Patient.builder()
            .businessId(source.getBusinessId())
            .version(nextVersion)
            .firstName(source.getFirstName())
            .lastName(source.getLastName())
            .dateOfBirth(source.getDateOfBirth())
            .gender(source.getGender())
            .phoneNumber(source.getPhoneNumber())
            .email(source.getEmail())
            .addressLine1(source.getAddressLine1())
            .addressLine2(source.getAddressLine2())
            .city(source.getCity())
            .state(source.getState())
            .zipCode(source.getZipCode())
            .photoIdVerified(source.getPhotoIdVerified())
            .isRegistrationComplete(source.getIsRegistrationComplete() != null ? source.getIsRegistrationComplete() : true)
            .status(PatientStatus.INACTIVE)
            .build();
        patientRepository.save(inactiveSource);

        // Write merge audit log
        String currentUser = getCurrentUsername();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", "MERGE");
        details.put("sourceId", sourceId.toString());
        details.put("targetId", targetId.toString());
        details.put("sourcePatientId", source.getPatientId());
        details.put("targetPatientId", target.getPatientId());
        details.put("entityCounts", counts);
        details.put("mergedBy", currentUser);

        AuditLog mergeLog = AuditLog.builder()
            .userId(currentUser)
            .action("UPDATE")
            .resourceType("PATIENT")
            .resourceId(targetId.toString())
            .timestamp(Instant.now())
            .details(details)
            .build();
        auditLogRepository.save(mergeLog);

        return patientService.getPatientByBusinessId(targetId);
    }

    private void validateMergeInputs(UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Source and target patients must be different");
        }
    }

    private Map<String, Long> buildChildCounts(UUID patientId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("emergencyContacts", emergencyContactRepository.countByPatientBusinessId(patientId));
        counts.put("medicalHistories", medicalHistoryRepository.countByPatientBusinessId(patientId));
        counts.put("insurance", insuranceRepository.countByPatientBusinessId(patientId));
        counts.put("photos", patientPhotoRepository.countByPatientBusinessId(patientId));
        counts.put("families", patientFamilyRepository.countByPatientBusinessId(patientId));
        counts.put("relationships", patientRelationshipRepository.countByPatientBusinessId(patientId));
        return counts;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "system";
    }
}
