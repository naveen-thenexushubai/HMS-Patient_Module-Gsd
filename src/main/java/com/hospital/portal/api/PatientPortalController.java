package com.hospital.portal.api;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.application.ConsentService;
import com.hospital.patient.domain.ConsentType;
import com.hospital.portal.api.dto.UpdateContactRequest;
import com.hospital.portal.application.PatientPortalService;
import com.hospital.security.audit.Audited;
import com.hospital.security.authorization.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Patient self-service portal endpoints.
 * All endpoints require ROLE_PATIENT and extract patientBusinessId from JWT token,
 * not from URL path — ensuring patients can only access their own data.
 */
@RestController
@RequestMapping("/api/portal/v1/me")
@Validated
public class PatientPortalController {

    @Autowired
    private PatientPortalService portalService;

    @Autowired
    private ConsentService consentService;

    /**
     * Extract the caller's patientBusinessId from the JWT token details.
     * This is the ONLY trusted source of identity for portal endpoints.
     */
    private UUID getCallerPatientBusinessId() {
        String id = SecurityContextHelper.getPatientBusinessId();
        if (id == null) {
            throw new IllegalStateException("No patientBusinessId in JWT token");
        }
        return UUID.fromString(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "READ", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> getOwnProfile() {
        return ResponseEntity.ok(portalService.getOwnProfile(getCallerPatientBusinessId()));
    }

    @PutMapping("/contact")
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> updateContact(
        @RequestBody UpdateContactRequest request
    ) {
        return ResponseEntity.ok(
            portalService.updateOwnContact(getCallerPatientBusinessId(), request));
    }

    @PutMapping("/insurance")
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<InsuranceDto> updateInsurance(
        @Valid @RequestBody InsuranceDto request
    ) {
        return ResponseEntity.ok(
            portalService.updateOwnInsurance(getCallerPatientBusinessId(), request));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "READ", resourceType = "APPOINTMENT")
    public ResponseEntity<List<AppointmentSummaryDto>> getOwnAppointments() {
        return ResponseEntity.ok(
            portalService.getOwnAppointments(getCallerPatientBusinessId()));
    }

    @PutMapping("/pre-registration")
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "UPDATE", resourceType = "PATIENT")
    public ResponseEntity<PatientDetailResponse> completePreRegistration(
        @Valid @RequestBody UpdatePatientRequest request
    ) {
        return ResponseEntity.ok(
            portalService.completePreRegistration(getCallerPatientBusinessId(), request));
    }

    @GetMapping("/consents")
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "READ", resourceType = "CONSENT")
    public ResponseEntity<List<ConsentRecordDto>> getOwnConsents() {
        return ResponseEntity.ok(
            consentService.getConsents(getCallerPatientBusinessId()));
    }

    @PostMapping("/consents/{type}/sign")
    @PreAuthorize("hasRole('PATIENT')")
    @Audited(action = "UPDATE", resourceType = "CONSENT")
    public ResponseEntity<ConsentRecordDto> signOwnConsent(
        @PathVariable ConsentType type,
        @RequestBody(required = false) SignConsentRequest request
    ) {
        return ResponseEntity.ok(
            consentService.signConsent(
                getCallerPatientBusinessId(),
                type,
                request != null ? request : new SignConsentRequest()
            )
        );
    }
}
