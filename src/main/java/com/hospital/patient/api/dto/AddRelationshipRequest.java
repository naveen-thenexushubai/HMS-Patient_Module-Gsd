package com.hospital.patient.api.dto;

import com.hospital.patient.domain.RelationshipType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Request to add a relationship to a patient.
 * Either relatedPatientBusinessId (another registered patient) OR
 * relatedPersonName (an external person) must be provided.
 */
@Data
public class AddRelationshipRequest {

    /** UUID of another registered patient. Null if the related person is not in the system. */
    private UUID relatedPatientBusinessId;

    /** Name of the related person when not a registered patient. */
    @Size(max = 100)
    private String relatedPersonName;

    @Size(max = 20)
    private String relatedPersonPhone;

    @Size(max = 255)
    private String relatedPersonEmail;

    @NotNull
    private RelationshipType relationshipType;

    private Boolean isGuarantor;

    @Size(max = 100)
    private String guarantorAccountId;

    private String notes;
}
