package com.hospital.patient.api;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.application.LabService;
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
@RequestMapping("/api/v1")
public class LabController {

    @Autowired
    private LabService labService;

    @PostMapping("/patients/{businessId}/lab-orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "LAB_ORDER")
    public ResponseEntity<LabOrderDto> createLabOrder(
        @PathVariable UUID businessId,
        @Valid @RequestBody CreateLabOrderRequest request
    ) {
        LabOrderDto result = labService.createLabOrder(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/patients/{businessId}/lab-orders")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "READ", resourceType = "LAB_ORDER")
    public ResponseEntity<List<LabOrderDto>> getLabOrders(@PathVariable UUID businessId) {
        return ResponseEntity.ok(labService.getLabOrdersWithResults(businessId));
    }

    @PatchMapping("/lab-orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<LabOrderDto> updateOrderStatus(
        @PathVariable UUID orderId,
        @Valid @RequestBody UpdateLabOrderStatusRequest request
    ) {
        LabOrderDto result = labService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/lab-orders/{orderId}/results")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "LAB_RESULT")
    public ResponseEntity<LabResultDto> addLabResult(
        @PathVariable UUID orderId,
        @Valid @RequestBody AddLabResultRequest request
    ) {
        LabResultDto result = labService.addLabResult(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/patients/{businessId}/lab-orders/abnormal")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<List<LabResultDto>> getAbnormalResults(@PathVariable UUID businessId) {
        return ResponseEntity.ok(labService.getAbnormalResults(businessId));
    }
}
