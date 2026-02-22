package com.hospital.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class LinkFamilyRequest {

    /**
     * Existing household to join. If null, a new household is created
     * with this patient as the head.
     */
    private UUID householdId;

    @NotBlank
    @Size(max = 50)
    private String relationshipToHead;

    /**
     * Whether this patient is the household head.
     * Defaults to false unless householdId is null (new household always sets head=true).
     */
    private Boolean isHead;
}
