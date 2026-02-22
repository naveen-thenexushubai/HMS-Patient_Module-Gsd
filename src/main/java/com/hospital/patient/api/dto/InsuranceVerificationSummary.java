package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for the on-demand insurance verification trigger.
 * Returned by POST /api/v1/admin/insurance/verify-all
 */
@Data
@Builder
public class InsuranceVerificationSummary {
    private int processedCount;
    private int verifiedCount;
    private int incompleteCount;
    private int staleCount;
    private int pendingCount;
}
