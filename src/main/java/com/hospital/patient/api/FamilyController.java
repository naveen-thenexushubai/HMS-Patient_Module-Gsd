package com.hospital.patient.api;

import com.hospital.patient.api.dto.HouseholdResponse;
import com.hospital.patient.api.dto.LinkFamilyRequest;
import com.hospital.patient.application.FamilyService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class FamilyController {

    @Autowired
    private FamilyService familyService;

    /**
     * POST /api/v1/patients/{businessId}/family
     * Links a patient to a household.
     * If request body has no householdId, a new household is created with this patient as head.
     * If householdId is provided, the patient joins that existing household.
     */
    @PostMapping("/api/v1/patients/{businessId}/family")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<HouseholdResponse> linkToFamily(
        @PathVariable UUID businessId,
        @Valid @RequestBody LinkFamilyRequest request
    ) {
        HouseholdResponse response = familyService.linkToFamily(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/patients/{businessId}/family
     * Returns the household this patient belongs to, including all members.
     * Returns 404 if the patient is not in any household.
     */
    @GetMapping("/api/v1/patients/{businessId}/family")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<HouseholdResponse> getFamily(@PathVariable UUID businessId) {
        HouseholdResponse response = familyService.getFamily(businessId);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/patients/{businessId}/family
     * Removes the patient from their household.
     * If the patient was the household head, the next member is promoted.
     */
    @DeleteMapping("/api/v1/patients/{businessId}/family")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<Void> unlinkFromFamily(@PathVariable UUID businessId) {
        familyService.unlinkFromFamily(businessId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/families/{householdId}/members
     * Returns all members of a household by its UUID.
     */
    @GetMapping("/api/v1/families/{householdId}/members")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<HouseholdResponse> getHouseholdMembers(@PathVariable UUID householdId) {
        HouseholdResponse response = familyService.getHouseholdMembers(householdId);
        return ResponseEntity.ok(response);
    }
}
