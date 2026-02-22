package com.hospital.security.audit;

import com.hospital.security.audit.dto.AuditLogDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    /**
     * GET /api/v1/admin/audit-logs
     * Admin-only global audit log view with optional filters.
     *
     * Query params (all optional):
     *   userId       — filter by user who performed the action
     *   action       — filter by action type (CREATE, READ, UPDATE, DELETE, SEARCH)
     *   resourceType — filter by resource type (PATIENT, INSURANCE, etc.)
     *   resourceId   — filter by resource ID
     *   startDate    — ISO-8601 timestamp (inclusive lower bound)
     *   endDate      — ISO-8601 timestamp (inclusive upper bound)
     *   page, size   — pagination
     */
    @GetMapping("/api/v1/admin/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "READ", resourceType = "AUDIT_LOG")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) String resourceId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<AuditLogDto> logs = auditLogService.getAuditLogs(
            userId, action, resourceType, resourceId, startDate, endDate, pageable
        );
        return ResponseEntity.ok(logs);
    }

    /**
     * GET /api/v1/patients/{businessId}/audit-logs
     * Returns audit trail for a specific patient (resourceType=PATIENT, resourceId=businessId).
     * All roles can view the patient's audit trail.
     */
    @GetMapping("/api/v1/patients/{businessId}/audit-logs")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "AUDIT_LOG")
    public ResponseEntity<Page<AuditLogDto>> getPatientAuditLogs(
        @PathVariable UUID businessId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<AuditLogDto> logs = auditLogService.getLogsForPatient(businessId, pageable);
        return ResponseEntity.ok(logs);
    }
}
