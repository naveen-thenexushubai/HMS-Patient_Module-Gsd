package com.hospital.patient.application;

import com.hospital.patient.api.dto.AddRelationshipRequest;
import com.hospital.patient.api.dto.PatientRelationshipDto;
import com.hospital.patient.domain.PatientRelationship;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientRelationshipRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RelationshipService {

    @Autowired
    private PatientRelationshipRepository relationshipRepository;

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Adds a typed relationship to a patient record.
     * Either relatedPatientBusinessId (another patient) or relatedPersonName (external person)
     * must be provided.
     *
     * @throws PatientNotFoundException  if the patient or related patient doesn't exist
     * @throws IllegalArgumentException  if neither relatedPatientBusinessId nor relatedPersonName is provided
     */
    public PatientRelationshipDto addRelationship(UUID patientBusinessId, AddRelationshipRequest request) {
        // Verify the patient exists
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        // Validate: must have either a linked patient or an external person name
        if (request.getRelatedPatientBusinessId() == null
                && (request.getRelatedPersonName() == null || request.getRelatedPersonName().isBlank())) {
            throw new IllegalArgumentException(
                "Either relatedPatientBusinessId or relatedPersonName must be provided");
        }

        // If linking to another patient, verify they exist
        if (request.getRelatedPatientBusinessId() != null) {
            patientRepository.findLatestVersionByBusinessId(request.getRelatedPatientBusinessId())
                .orElseThrow(() -> new PatientNotFoundException(
                    request.getRelatedPatientBusinessId().toString()));
        }

        PatientRelationship relationship = PatientRelationship.builder()
            .patientBusinessId(patientBusinessId)
            .relatedPatientBusinessId(request.getRelatedPatientBusinessId())
            .relatedPersonName(request.getRelatedPersonName())
            .relatedPersonPhone(request.getRelatedPersonPhone())
            .relatedPersonEmail(request.getRelatedPersonEmail())
            .relationshipType(request.getRelationshipType())
            .isGuarantor(request.getIsGuarantor() != null ? request.getIsGuarantor() : false)
            .guarantorAccountId(request.getGuarantorAccountId())
            .notes(request.getNotes())
            .build();

        PatientRelationship saved = relationshipRepository.save(relationship);
        return toDto(saved);
    }

    /**
     * Returns all relationships for a patient, enriched with related patient name if applicable.
     */
    @Transactional(readOnly = true)
    public List<PatientRelationshipDto> getRelationships(UUID patientBusinessId) {
        // Verify patient exists
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        return relationshipRepository.findByPatientBusinessId(patientBusinessId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Deletes a relationship, verifying it belongs to the specified patient.
     *
     * @throws PatientNotFoundException if the relationship doesn't exist or doesn't belong to the patient
     */
    public void deleteRelationship(UUID patientBusinessId, Long relationshipId) {
        if (!relationshipRepository.existsByIdAndPatientBusinessId(relationshipId, patientBusinessId)) {
            throw new PatientNotFoundException(
                "Relationship " + relationshipId + " not found for patient " + patientBusinessId);
        }
        relationshipRepository.deleteById(relationshipId);
    }

    private PatientRelationshipDto toDto(PatientRelationship r) {
        String relatedPatientName = null;
        if (r.getRelatedPatientBusinessId() != null) {
            relatedPatientName = patientRepository
                .findLatestVersionByBusinessId(r.getRelatedPatientBusinessId())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse(null);
        }

        return PatientRelationshipDto.builder()
            .id(r.getId())
            .patientBusinessId(r.getPatientBusinessId())
            .relatedPatientBusinessId(r.getRelatedPatientBusinessId())
            .relatedPatientName(relatedPatientName)
            .relatedPersonName(r.getRelatedPersonName())
            .relatedPersonPhone(r.getRelatedPersonPhone())
            .relatedPersonEmail(r.getRelatedPersonEmail())
            .relationshipType(r.getRelationshipType())
            .isGuarantor(r.getIsGuarantor())
            .guarantorAccountId(r.getGuarantorAccountId())
            .notes(r.getNotes())
            .createdAt(r.getCreatedAt())
            .createdBy(r.getCreatedBy())
            .build();
    }
}
