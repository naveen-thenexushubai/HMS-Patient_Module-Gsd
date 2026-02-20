package com.hospital.patient.api;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.application.*;
import com.hospital.patient.domain.*;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/patients")
@Validated
public class PatientController {
    @Autowired
    private PatientService patientService;

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "PATIENT")
    public ResponseEntity<?> registerPatient(
        @Valid @RequestBody RegisterPatientRequest request,
        @RequestParam(required = false, defaultValue = "false") boolean overrideDuplicate
    ) {
        DuplicateDetectionService.DuplicateCheckResult duplicateCheck =
            patientService.checkForDuplicates(request);

        if (duplicateCheck.hasDuplicates() && !overrideDuplicate) {
            DuplicateWarningResponse warning = DuplicateWarningResponse.builder()
                .matches(duplicateCheck.getMatches().stream()
                    .map(this::toDuplicateMatchDto)
                    .collect(Collectors.toList()))
                .requiresOverride(duplicateCheck.isShouldBlockRegistration())
                .message("Potential duplicate patient(s) detected. Review matches and confirm registration.")
                .build();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(warning);
        }

        if (duplicateCheck.isShouldBlockRegistration() && overrideDuplicate) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("High-confidence duplicate detected. Admin approval required.");
        }

        PatientDetailResponse patient = patientService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "SEARCH", resourceType = "PATIENT")
    public ResponseEntity<Slice<PatientSummaryResponse>> searchPatients(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) PatientStatus status,
        @RequestParam(required = false) Gender gender,
        @RequestParam(required = false) String bloodGroup,
        @PageableDefault(size = 20, sort = "lastName") Pageable pageable
    ) {
        Slice<PatientSummaryResponse> patients = patientService.searchPatients(
            query, status, gender, bloodGroup, pageable
        );
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/{businessId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> getPatient(@PathVariable UUID businessId) {
        PatientDetailResponse patient = patientService.getPatientByBusinessId(businessId);
        return ResponseEntity.ok(patient);
    }

    /**
     * PUT /api/v1/patients/{businessId}
     * Update patient demographics. Inserts a new patient version row (event-sourced pattern).
     *
     * Authorization: RECEPTIONIST or ADMIN only (PatientPermissionEvaluator: write permission).
     * DOCTOR and NURSE are read-only — they will receive 403 Forbidden.
     *
     * UPD-01, UPD-02, UPD-03, UPD-04, UPD-05, UPD-06, UPD-07, UPD-10
     */
    @PutMapping("/{businessId}")
    @PreAuthorize("hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> updatePatient(
        @PathVariable UUID businessId,
        @Valid @RequestBody UpdatePatientRequest request
    ) {
        PatientDetailResponse updated = patientService.updatePatient(businessId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/v1/patients/{businessId}/status
     * Change patient status (ACTIVE/INACTIVE). Inserts a new patient version row.
     *
     * Authorization: ADMIN only (status changes are sensitive — STAT-01 specifies admin).
     *
     * STAT-01, STAT-02, STAT-03, STAT-04, STAT-05, STAT-06
     */
    @PatchMapping("/{businessId}/status")
    @PreAuthorize("hasRole('ADMIN') and hasPermission(#businessId, 'Patient', 'write')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> changeStatus(
        @PathVariable UUID businessId,
        @Valid @RequestBody UpdateStatusRequest request
    ) {
        PatientDetailResponse updated = patientService.changePatientStatus(businessId, request.getStatus());
        return ResponseEntity.ok(updated);
    }

    private DuplicateMatchDto toDuplicateMatchDto(DuplicateDetectionService.DuplicateMatch match) {
        Patient p = match.getPatient();
        return DuplicateMatchDto.builder()
            .patientId(p.getPatientId())
            .fullName(p.getFirstName() + " " + p.getLastName())
            .dateOfBirth(p.getDateOfBirth())
            .phoneNumber(p.getPhoneNumber())
            .email(p.getEmail())
            .similarityScore((int) (match.getScore() * 100))
            .build();
    }
}
