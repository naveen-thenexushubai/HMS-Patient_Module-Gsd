package com.hospital.patient.api;

import com.hospital.patient.api.dto.CreatePrescriptionRequest;
import com.hospital.patient.api.dto.DiscontinuePrescriptionRequest;
import com.hospital.patient.api.dto.PrescriptionDto;
import com.hospital.patient.application.PrescriptionService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{businessId}/prescriptions")
public class PrescriptionController {

    @Autowired
    private PrescriptionService prescriptionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "PRESCRIPTION")
    public ResponseEntity<PrescriptionDto> createPrescription(
        @PathVariable UUID businessId,
        @Valid @RequestBody CreatePrescriptionRequest request
    ) {
        PrescriptionDto result = prescriptionService.createPrescription(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "READ", resourceType = "PRESCRIPTION")
    public ResponseEntity<List<PrescriptionDto>> getPrescriptions(@PathVariable UUID businessId) {
        return ResponseEntity.ok(prescriptionService.getPrescriptions(businessId));
    }

    @PatchMapping("/{prescriptionId}/discontinue")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Audited(action = "UPDATE", resourceType = "PRESCRIPTION")
    public ResponseEntity<PrescriptionDto> discontinuePrescription(
        @PathVariable UUID businessId,
        @PathVariable UUID prescriptionId,
        @RequestBody DiscontinuePrescriptionRequest request
    ) {
        PrescriptionDto result = prescriptionService.discontinuePrescription(
            prescriptionId, request.getDiscontinueReason());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{prescriptionId}/refill")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "UPDATE", resourceType = "PRESCRIPTION")
    public ResponseEntity<PrescriptionDto> requestRefill(
        @PathVariable UUID businessId,
        @PathVariable UUID prescriptionId
    ) {
        PrescriptionDto result = prescriptionService.requestRefill(prescriptionId);
        return ResponseEntity.ok(result);
    }
}
