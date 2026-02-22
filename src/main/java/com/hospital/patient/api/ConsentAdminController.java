package com.hospital.patient.api;

import com.hospital.patient.api.dto.PatientMissingConsentDto;
import com.hospital.patient.application.ConsentService;
import com.hospital.security.audit.Audited;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/consents")
public class ConsentAdminController {

    @Autowired
    private ConsentService consentService;

    @GetMapping("/missing")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "READ", resourceType = "CONSENT")
    public ResponseEntity<Page<PatientMissingConsentDto>> getMissingConsents(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(consentService.getPatientsWithMissingConsents(pageable));
    }
}
