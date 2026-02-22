package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LabResultDto {

    private UUID businessId;
    private UUID labOrderBusinessId;
    private UUID patientBusinessId;
    private String testName;
    private String resultValue;
    private String unit;
    private String referenceRange;
    private boolean abnormal;
    private String abnormalFlag;
    private String resultText;
    private String documentFilename;
    private String reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt;
    private String createdBy;
}
