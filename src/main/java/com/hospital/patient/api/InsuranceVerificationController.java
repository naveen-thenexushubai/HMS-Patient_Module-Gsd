package com.hospital.patient.api;

import com.hospital.patient.api.dto.InsuranceVerificationReportItem;
import com.hospital.patient.api.dto.InsuranceVerificationSummary;
import com.hospital.patient.application.InsuranceVerificationService;
import com.hospital.patient.domain.InsuranceVerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only endpoints for bulk insurance verification.
 */
@RestController
@RequestMapping("/api/v1/admin/insurance")
@PreAuthorize("hasRole('ADMIN')")
public class InsuranceVerificationController {

    @Autowired
    private InsuranceVerificationService insuranceVerificationService;

    /**
     * POST /api/v1/admin/insurance/verify-all
     * Triggers immediate verification of all active insurance records.
     * Returns a summary of verification results.
     */
    @PostMapping("/verify-all")
    public ResponseEntity<InsuranceVerificationSummary> verifyAll() {
        InsuranceVerificationSummary summary = insuranceVerificationService.verifyAll();
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/v1/admin/insurance/verification-report
     * Returns a paginated list of insurance records not in VERIFIED status.
     * Filter by status: INCOMPLETE, STALE, or PENDING.
     */
    @GetMapping("/verification-report")
    public ResponseEntity<Page<InsuranceVerificationReportItem>> getVerificationReport(
        @RequestParam(required = false) InsuranceVerificationStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<InsuranceVerificationReportItem> report =
            insuranceVerificationService.getVerificationReport(status, pageable);
        return ResponseEntity.ok(report);
    }
}
