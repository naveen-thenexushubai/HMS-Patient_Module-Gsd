package com.hospital.patient.api;

import com.hospital.patient.application.PhotoService;
import com.hospital.patient.domain.PatientPhoto;
import com.hospital.security.audit.Audited;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Patient photo upload and download endpoints.
 *
 * POST /api/v1/patients/{businessId}/photo
 *   - Accepts multipart/form-data with "file" parameter (JPEG or PNG, max 5MB)
 *   - Requires RECEPTIONIST or ADMIN role
 *   - Returns 201 with PatientPhoto metadata (filename, contentType, fileSizeBytes, uploadedAt)
 *
 * GET /api/v1/patients/{businessId}/photo
 *   - Returns current photo as binary stream
 *   - Requires any authenticated role (RECEPTIONIST, ADMIN, DOCTOR, NURSE)
 *   - Returns 404 if no photo uploaded for patient
 *
 * Photos are HIPAA PHI — served only through this authenticated endpoint, never from static path.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PhotoController {

    @Autowired
    private PhotoService photoService;

    /**
     * Upload patient photo.
     * Accepts multipart/form-data with part name "file".
     * Content-Type must be image/jpeg or image/png.
     * Max size: 5MB (configured in application.yml spring.servlet.multipart.max-file-size).
     */
    @PostMapping(value = "/{businessId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "PATIENT_PHOTO")
    public ResponseEntity<PatientPhoto> uploadPhoto(
        @PathVariable UUID businessId,
        @RequestParam("file") MultipartFile file
    ) {
        PatientPhoto saved = photoService.uploadPhoto(businessId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Download current patient photo.
     * Streams the file binary with correct Content-Type header.
     * Returns 404 if patient has no uploaded photo.
     */
    @GetMapping("/{businessId}/photo")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR', 'NURSE')")
    @Audited(action = "READ", resourceType = "PATIENT_PHOTO")
    public ResponseEntity<Resource> getPhoto(@PathVariable UUID businessId) {
        Object[] result = photoService.getCurrentPhoto(businessId);
        Resource resource = (Resource) result[0];
        String contentType = (String) result[1];

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"patient-photo\"")
            .body(resource);
    }
}
