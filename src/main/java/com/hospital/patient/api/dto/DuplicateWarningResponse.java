package com.hospital.patient.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response returned when potential duplicate patients are detected during registration.
 * HTTP status: 409 Conflict
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateWarningResponse {

    /**
     * List of potential duplicate matches with similarity scores.
     */
    private List<DuplicateMatchDto> matches;

    /**
     * Whether this duplicate requires override permission.
     * True for 90%+ similarity (high confidence), false for 85-89% (warning only).
     */
    private boolean requiresOverride;

    /**
     * Human-readable message explaining the duplicate detection.
     */
    private String message;
}
