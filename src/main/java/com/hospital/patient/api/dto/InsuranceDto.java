package com.hospital.patient.api.dto;

import com.hospital.patient.domain.CoverageType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;

/**
 * DTO for POST /api/v1/patients/{businessId}/insurance (create)
 * and PUT /api/v1/patients/{businessId}/insurance (update).
 * Also used as the response body (includes id, createdAt, updatedAt audit fields).
 *
 * policyNumber and groupNumber are plaintext in this DTO.
 * Encryption/decryption happens at the JPA layer via SensitiveDataConverter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceDto {

    private Long id;

    @NotBlank(message = "Insurance provider name is required")
    @Size(max = 255, message = "Provider name must not exceed 255 characters")
    private String providerName;

    @NotBlank(message = "Policy number is required")
    @Pattern(
        regexp = "^[A-Za-z0-9\\-]{3,50}$",
        message = "Policy number must be 3-50 alphanumeric characters (hyphens allowed)"
    )
    private String policyNumber;

    @Pattern(
        regexp = "^[A-Za-z0-9\\-]{0,50}$",
        message = "Group number must be up to 50 alphanumeric characters (hyphens allowed)"
    )
    private String groupNumber;

    @NotNull(message = "Coverage type is required")
    private CoverageType coverageType;

    private Boolean isActive;

    // Response-only audit fields (null on request, populated on response)
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
