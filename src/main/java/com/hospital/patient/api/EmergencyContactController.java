package com.hospital.patient.api;

import com.hospital.patient.api.dto.EmergencyContactDto;
import com.hospital.patient.application.EmergencyContactService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{businessId}/emergency-contacts")
@Validated
public class EmergencyContactController {

    @Autowired
    private EmergencyContactService emergencyContactService;

    /**
     * POST /api/v1/patients/{businessId}/emergency-contacts
     * Add a new emergency contact for the patient (EMR-01).
     * Requires write permission (RECEPTIONIST or ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<EmergencyContactDto> addContact(
        @PathVariable UUID businessId,
        @Valid @RequestBody EmergencyContactDto request
    ) {
        EmergencyContactDto created = emergencyContactService.addContact(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/patients/{businessId}/emergency-contacts
     * List all emergency contacts for a patient (EMR-03).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<List<EmergencyContactDto>> listContacts(
        @PathVariable UUID businessId
    ) {
        return ResponseEntity.ok(emergencyContactService.listContacts(businessId));
    }

    /**
     * PUT /api/v1/patients/{businessId}/emergency-contacts/{contactId}
     * Update an existing emergency contact (EMR-04).
     * Ownership verification: contactId must belong to businessId.
     */
    @PutMapping("/{contactId}")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<EmergencyContactDto> updateContact(
        @PathVariable UUID businessId,
        @PathVariable Long contactId,
        @Valid @RequestBody EmergencyContactDto request
    ) {
        EmergencyContactDto updated = emergencyContactService.updateContact(businessId, contactId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/v1/patients/{businessId}/emergency-contacts/{contactId}
     * Remove an emergency contact (EMR-04).
     * Ownership verification: contactId must belong to businessId.
     */
    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "DELETE", resourceType = "PATIENT")
    public ResponseEntity<Void> deleteContact(
        @PathVariable UUID businessId,
        @PathVariable Long contactId
    ) {
        emergencyContactService.deleteContact(businessId, contactId);
        return ResponseEntity.noContent().build();
    }
}
