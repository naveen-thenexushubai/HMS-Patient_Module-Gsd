package com.hospital.patient.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for the patient merge endpoint.
 */
@Data
public class MergeRequest {

    @NotNull(message = "sourceId is required")
    private UUID sourceId;

    @NotNull(message = "targetId is required")
    private UUID targetId;
}
