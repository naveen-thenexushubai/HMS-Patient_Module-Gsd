package com.hospital.portal.api;

import com.hospital.portal.api.dto.PortalLoginRequest;
import com.hospital.portal.api.dto.PortalLoginResponse;
import com.hospital.portal.api.dto.PortalRegisterRequest;
import com.hospital.portal.application.PatientPortalService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public patient portal authentication endpoints.
 * No @Audited annotation — these are public endpoints (no staff auth context).
 * No @PreAuthorize — SecurityConfig permits /api/portal/auth/** without authentication.
 */
@RestController
@RequestMapping("/api/portal/auth")
@Validated
public class PatientPortalAuthController {

    @Autowired
    private PatientPortalService portalService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerPortalAccount(
        @Valid @RequestBody PortalRegisterRequest request
    ) {
        portalService.registerPortalAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("message", "Portal account created successfully. You can now log in."));
    }

    @PostMapping("/login")
    public ResponseEntity<PortalLoginResponse> loginPortal(
        @Valid @RequestBody PortalLoginRequest request
    ) {
        return ResponseEntity.ok(portalService.loginPortal(request));
    }
}
