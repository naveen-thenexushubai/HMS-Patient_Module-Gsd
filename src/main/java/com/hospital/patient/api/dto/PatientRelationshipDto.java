package com.hospital.patient.api.dto;

import com.hospital.patient.domain.RelationshipType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PatientRelationshipDto {
    private Long id;
    private UUID patientBusinessId;

    // Related party — one of these two will be populated
    private UUID relatedPatientBusinessId;
    private String relatedPatientName;    // enriched from patient record if relatedPatientBusinessId set

    private String relatedPersonName;
    private String relatedPersonPhone;
    private String relatedPersonEmail;

    private RelationshipType relationshipType;
    private Boolean isGuarantor;
    private String guarantorAccountId;
    private String notes;

    private Instant createdAt;
    private String createdBy;
}
