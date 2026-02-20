package com.hospital.patient.api;

import com.hospital.patient.api.dto.DataQualityReport;
import com.hospital.patient.application.DataQualityService;
import com.hospital.security.audit.Audited;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Data quality dashboard endpoint (SC3 — data quality dashboard).
 *
 * GET /api/v1/admin/data-quality
 *   - Requires ADMIN role
 *   - Returns DataQualityReport with real-time counts
 *   - No caching: report reflects current database state
 *
 * The report helps admins identify:
 * - incompleteRegistrations: quick-registered patients needing follow-up
 * - missingInsurance: patients without active insurance (billing risk)
 * - missingPhoto: patients without a current photo on file
 * - unverifiedPhotoIds: patients whose physical ID document was not verified
 */
@RestController
@RequestMapping("/api/v1/admin")
public class DataQualityController {

    @Autowired
    private DataQualityService dataQualityService;

    /**
     * Get real-time data quality metrics for all active patients.
     * ADMIN role required — aggregate data should not be exposed to clinical staff.
     */
    @GetMapping("/data-quality")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "READ", resourceType = "DATA_QUALITY_REPORT")
    public ResponseEntity<DataQualityReport> getDataQualityReport() {
        DataQualityReport report = dataQualityService.getDataQualityReport();
        return ResponseEntity.ok(report);
    }
}
