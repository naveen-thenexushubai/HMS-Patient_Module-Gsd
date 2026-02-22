package com.hospital.patient.api.dto;

import com.hospital.patient.domain.ConsentStatus;
import com.hospital.patient.domain.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecordDto {
    private Long id;
    private UUID businessId;
    private UUID patientBusinessId;
    private ConsentType consentType;
    private ConsentStatus status;
    private Instant signedAt;
    private String signedBy;
    private Instant expiresAt;
    private String formVersion;
    private String notes;
    private boolean hasDocument;
    private String documentFilename;
    private String ipAddress;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
}
