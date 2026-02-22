package com.hospital.patient.api.dto;

import com.hospital.patient.domain.CoverageType;
import com.hospital.patient.domain.InsuranceVerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * One row in the insurance verification report.
 * Returned by GET /api/v1/admin/insurance/verification-report
 */
@Data
@Builder
public class InsuranceVerificationReportItem {
    private Long insuranceId;
    private UUID patientBusinessId;
    private String patientId;
    private String patientFullName;
    private String providerName;
    private CoverageType coverageType;
    private InsuranceVerificationStatus verificationStatus;
    private Instant lastVerifiedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
