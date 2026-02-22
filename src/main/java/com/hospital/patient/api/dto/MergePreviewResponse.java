package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Response DTO for the merge preview endpoint.
 * Shows both patients side-by-side and counts of child entities that will be moved.
 */
@Data
@Builder
public class MergePreviewResponse {

    /** The source patient that will be deactivated after merge. */
    private PatientDetailResponse sourcePatient;

    /** The target patient that will retain all data after merge. */
    private PatientDetailResponse targetPatient;

    /**
     * Count of child entities currently owned by the source patient.
     * These will all be reassigned to the target patient during merge.
     * Keys: emergencyContacts, medicalHistories, insurance, photos, families, relationships
     */
    private Map<String, Long> sourceChildEntityCounts;

    /** Human-readable summary of what the merge will do. */
    private String mergeDescription;
}
