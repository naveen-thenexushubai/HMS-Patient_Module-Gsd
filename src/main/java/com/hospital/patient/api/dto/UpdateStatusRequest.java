package com.hospital.patient.api.dto;

import com.hospital.patient.domain.PatientStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for PATCH /api/v1/patients/{businessId}/status.
 * Only admin can change status (STAT-01, STAT-02).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private PatientStatus status;
}
