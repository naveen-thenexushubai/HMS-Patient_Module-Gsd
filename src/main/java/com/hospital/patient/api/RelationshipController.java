package com.hospital.patient.api;

import com.hospital.patient.api.dto.AddRelationshipRequest;
import com.hospital.patient.api.dto.PatientRelationshipDto;
import com.hospital.patient.application.RelationshipService;
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
@RequestMapping("/api/v1/patients/{businessId}/relationships")
public class RelationshipController {

    @Autowired
    private RelationshipService relationshipService;

    /**
     * POST /api/v1/patients/{businessId}/relationships
     * Adds a typed relationship to a patient.
     * Provide relatedPatientBusinessId (another registered patient) OR
     * relatedPersonName (an external person) — not both required.
     */
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<PatientRelationshipDto> addRelationship(
        @PathVariable UUID businessId,
        @Valid @RequestBody AddRelationshipRequest request
    ) {
        PatientRelationshipDto dto = relationshipService.addRelationship(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * GET /api/v1/patients/{businessId}/relationships
     * Returns all relationships for a patient.
     * If the related party is a registered patient, their name is included.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<List<PatientRelationshipDto>> getRelationships(@PathVariable UUID businessId) {
        List<PatientRelationshipDto> relationships = relationshipService.getRelationships(businessId);
        return ResponseEntity.ok(relationships);
    }

    /**
     * DELETE /api/v1/patients/{businessId}/relationships/{relationshipId}
     * Removes a relationship. Returns 404 if the relationship doesn't belong to this patient.
     */
    @DeleteMapping("/{relationshipId}")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "DELETE", resourceType = "PATIENT")
    public ResponseEntity<Void> deleteRelationship(
        @PathVariable UUID businessId,
        @PathVariable Long relationshipId
    ) {
        relationshipService.deleteRelationship(businessId, relationshipId);
        return ResponseEntity.noContent().build();
    }
}
