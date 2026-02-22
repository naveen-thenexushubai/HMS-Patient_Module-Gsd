package com.hospital.patient.api;

import com.hospital.patient.api.dto.ConsentRecordDto;
import com.hospital.patient.api.dto.SignConsentRequest;
import com.hospital.patient.application.ConsentService;
import com.hospital.patient.domain.ConsentType;
import com.hospital.security.audit.Audited;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{businessId}/consents")
@Validated
public class ConsentController {

    @Autowired
    private ConsentService consentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "CONSENT")
    public ResponseEntity<List<ConsentRecordDto>> listConsents(@PathVariable UUID businessId) {
        return ResponseEntity.ok(consentService.getConsents(businessId));
    }

    @PostMapping("/{type}/sign")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "UPDATE", resourceType = "CONSENT")
    public ResponseEntity<ConsentRecordDto> signConsent(
        @PathVariable UUID businessId,
        @PathVariable ConsentType type,
        @RequestBody(required = false) SignConsentRequest request
    ) {
        return ResponseEntity.ok(consentService.signConsent(businessId, type,
            request != null ? request : new SignConsentRequest()));
    }

    @PostMapping("/{consentId}/document")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "CONSENT_DOCUMENT")
    public ResponseEntity<ConsentRecordDto> uploadDocument(
        @PathVariable UUID businessId,
        @PathVariable UUID consentId,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(consentService.uploadConsentDocument(businessId, consentId, file));
    }

    @GetMapping("/{consentId}/document")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "CONSENT_DOCUMENT")
    public ResponseEntity<Resource> downloadDocument(
        @PathVariable UUID businessId,
        @PathVariable UUID consentId
    ) {
        Object[] result = consentService.downloadConsentDocument(businessId, consentId);
        Resource resource = (Resource) result[0];
        String filename = (String) result[1];
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(resource);
    }

    @DeleteMapping("/{consentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "DELETE", resourceType = "CONSENT")
    public ResponseEntity<ConsentRecordDto> revokeConsent(
        @PathVariable UUID businessId,
        @PathVariable UUID consentId
    ) {
        return ResponseEntity.ok(consentService.revokeConsent(businessId, consentId));
    }
}
