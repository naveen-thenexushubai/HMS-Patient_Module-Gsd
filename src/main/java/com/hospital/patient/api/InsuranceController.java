package com.hospital.patient.api;

import com.hospital.patient.api.dto.InsuranceDto;
import com.hospital.patient.application.InsuranceService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{businessId}/insurance")
@Validated
public class InsuranceController {

    @Autowired
    private InsuranceService insuranceService;

    /**
     * GET /api/v1/patients/{businessId}/insurance
     * Returns the active insurance record for a patient (INS-04).
     * All authenticated roles can view insurance info.
     * Returns 404 if no active insurance exists.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<InsuranceDto> getInsurance(@PathVariable UUID businessId) {
        return insuranceService.getActiveInsurance(businessId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/v1/patients/{businessId}/insurance
     * Create insurance for a patient (INS-01).
     * Deactivates any existing active insurance before creating new one.
     * Requires write permission (RECEPTIONIST or ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<InsuranceDto> createInsurance(
        @PathVariable UUID businessId,
        @Valid @RequestBody InsuranceDto request
    ) {
        InsuranceDto created = insuranceService.createInsurance(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/v1/patients/{businessId}/insurance
     * Update existing active insurance (INS-05).
     * Returns 404 if no active insurance exists (use POST to create first).
     * Requires write permission (RECEPTIONIST or ADMIN).
     */
    @PutMapping
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<InsuranceDto> updateInsurance(
        @PathVariable UUID businessId,
        @Valid @RequestBody InsuranceDto request
    ) {
        InsuranceDto updated = insuranceService.updateInsurance(businessId, request);
        return ResponseEntity.ok(updated);
    }
}
