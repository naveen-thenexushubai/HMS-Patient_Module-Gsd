package com.hospital.patient.api;

import com.hospital.patient.api.dto.RecordVitalSignsRequest;
import com.hospital.patient.api.dto.VitalSignsDto;
import com.hospital.patient.application.VitalSignsService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{businessId}/vitals")
public class VitalSignsController {

    @Autowired
    private VitalSignsService vitalSignsService;

    @PostMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "CREATE", resourceType = "VITAL_SIGNS")
    public ResponseEntity<VitalSignsDto> recordVitalSigns(
        @PathVariable UUID businessId,
        @Valid @RequestBody RecordVitalSignsRequest request
    ) {
        VitalSignsDto result = vitalSignsService.recordVitalSigns(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "READ", resourceType = "VITAL_SIGNS")
    public ResponseEntity<Page<VitalSignsDto>> getVitalSigns(
        @PathVariable UUID businessId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(vitalSignsService.getVitalSigns(businessId, page, size));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<VitalSignsDto> getLatestVitalSigns(@PathVariable UUID businessId) {
        Optional<VitalSignsDto> latest = vitalSignsService.getLatestVitalSigns(businessId);
        return latest.map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }
}
