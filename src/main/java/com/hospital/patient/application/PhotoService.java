package com.hospital.patient.application;

import com.hospital.patient.domain.PatientPhoto;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientPhotoRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

/**
 * Service for patient photo management.
 *
 * Upload flow:
 * 1. Verify patient exists (PatientRepository.findLatestVersionByBusinessId)
 * 2. Store file on filesystem (FileStorageService.store) — validates content type + magic bytes
 * 3. Deactivate any existing current photo (PatientPhotoRepository.deactivateCurrentPhotos)
 * 4. Save new PatientPhoto metadata record with is_current=true
 *
 * Download flow:
 * 1. Find PatientPhoto with is_current=true for this patient
 * 2. Load file Resource via FileStorageService.load(filename)
 * 3. Return Resource + content type for controller to stream
 *
 * Photos are HIPAA PHI — all upload/download operations must be audited.
 * File access restricted to authenticated users via SecurityConfig.
 */
@Service
@Transactional
public class PhotoService {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientPhotoRepository photoRepository;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * Upload and store a patient photo.
     *
     * @param businessId patient's business UUID
     * @param file       multipart image file (JPEG or PNG, max 5MB)
     * @return saved PatientPhoto metadata
     * @throws PatientNotFoundException if patient does not exist
     * @throws IllegalArgumentException if file content type is invalid
     */
    public PatientPhoto uploadPhoto(UUID businessId, MultipartFile file) {
        // 1. Verify patient exists
        patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // 2. Store file (validates content type + magic bytes, generates UUID filename)
        String filename = fileStorageService.store(file);

        // 3. Deactivate previous current photo (if any)
        photoRepository.deactivateCurrentPhotos(businessId);

        // 4. Save metadata record
        PatientPhoto photo = PatientPhoto.builder()
            .patientBusinessId(businessId)
            .filename(filename)
            .contentType(file.getContentType())
            .fileSizeBytes(file.getSize())
            .uploadedBy(getCurrentUsername())
            .isCurrent(true)
            .build();

        return photoRepository.save(photo);
    }

    /**
     * Retrieve the current photo Resource for streaming.
     *
     * @param businessId patient's business UUID
     * @return array of [Resource, contentType String] for controller
     * @throws PatientNotFoundException if patient does not exist
     * @throws EntityNotFoundException  if patient has no current photo
     */
    @Transactional(readOnly = true)
    public Object[] getCurrentPhoto(UUID businessId) {
        // Verify patient exists
        patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // Find current photo metadata
        PatientPhoto photo = photoRepository.findByPatientBusinessIdAndIsCurrentTrue(businessId)
            .orElseThrow(() -> new EntityNotFoundException(
                "No photo found for patient: " + businessId));

        // Load Resource from filesystem
        Resource resource = fileStorageService.load(photo.getFilename());

        return new Object[]{resource, photo.getContentType()};
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "system";
    }
}
