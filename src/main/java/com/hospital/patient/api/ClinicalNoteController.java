package com.hospital.patient.api;

import com.hospital.patient.api.dto.ClinicalNoteDto;
import com.hospital.patient.api.dto.CreateClinicalNoteRequest;
import com.hospital.patient.api.dto.UpdateClinicalNoteRequest;
import com.hospital.patient.application.ClinicalNoteService;
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
@RequestMapping("/api/v1/patients/{businessId}/notes")
public class ClinicalNoteController {

    @Autowired
    private ClinicalNoteService clinicalNoteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "CLINICAL_NOTE")
    public ResponseEntity<ClinicalNoteDto> createNote(
        @PathVariable UUID businessId,
        @Valid @RequestBody CreateClinicalNoteRequest request
    ) {
        ClinicalNoteDto result = clinicalNoteService.createNote(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "READ", resourceType = "CLINICAL_NOTE")
    public ResponseEntity<List<ClinicalNoteDto>> getNotes(@PathVariable UUID businessId) {
        return ResponseEntity.ok(clinicalNoteService.getNotes(businessId));
    }

    @PutMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "UPDATE", resourceType = "CLINICAL_NOTE")
    public ResponseEntity<ClinicalNoteDto> updateNote(
        @PathVariable UUID businessId,
        @PathVariable UUID noteId,
        @Valid @RequestBody UpdateClinicalNoteRequest request
    ) {
        ClinicalNoteDto result = clinicalNoteService.updateNote(noteId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{noteId}/finalize")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Audited(action = "UPDATE", resourceType = "CLINICAL_NOTE")
    public ResponseEntity<ClinicalNoteDto> finalizeNote(
        @PathVariable UUID businessId,
        @PathVariable UUID noteId
    ) {
        ClinicalNoteDto result = clinicalNoteService.finalizeNote(noteId);
        return ResponseEntity.ok(result);
    }
}
