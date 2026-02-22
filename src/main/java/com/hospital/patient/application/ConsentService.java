package com.hospital.patient.application;

import com.hospital.patient.api.dto.ConsentAlertDto;
import com.hospital.patient.api.dto.ConsentRecordDto;
import com.hospital.patient.api.dto.PatientMissingConsentDto;
import com.hospital.patient.api.dto.SignConsentRequest;
import com.hospital.patient.domain.ConsentRecord;
import com.hospital.patient.domain.ConsentStatus;
import com.hospital.patient.domain.ConsentType;
import com.hospital.patient.infrastructure.ConsentRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.storage.ConsentDocumentStorageService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConsentService {

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ConsentDocumentStorageService documentStorageService;

    @Autowired(required = false)
    private HttpServletRequest httpServletRequest;

    /**
     * Get all consent records for a patient ordered by consent type.
     */
    @Transactional(readOnly = true)
    public List<ConsentRecordDto> getConsents(UUID patientBusinessId) {
        return consentRepository.findByPatientBusinessIdOrderByConsentTypeAsc(patientBusinessId)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Sign or re-sign a consent. Upsert pattern: one record per patient per type.
     * If existing record: updates to SIGNED with new signedAt, signedBy, expiresAt.
     * If no existing record: creates new SIGNED record.
     * expiresAt:
     *   HIPAA_NOTICE -> +1 year
     *   TREATMENT_AUTHORIZATION -> +5 years
     *   others -> null
     */
    public ConsentRecordDto signConsent(UUID patientBusinessId, ConsentType type, SignConsentRequest request) {
        String signer = getCurrentUsername();
        Instant now = Instant.now();
        Instant expiresAt = computeExpiresAt(type, now);
        String version = (request != null && StringUtils.hasText(request.getFormVersion()))
            ? request.getFormVersion() : "1.0";
        String notes = (request != null) ? request.getNotes() : null;

        ConsentRecord record = consentRepository
            .findByPatientBusinessIdAndConsentType(patientBusinessId, type)
            .orElseGet(() -> ConsentRecord.builder()
                .patientBusinessId(patientBusinessId)
                .consentType(type)
                .build());

        record.setStatus(ConsentStatus.SIGNED);
        record.setSignedAt(now);
        record.setSignedBy(signer);
        record.setExpiresAt(expiresAt);
        record.setFormVersion(version);
        if (notes != null) {
            record.setNotes(notes);
        }
        record.setIpAddress(getClientIp());

        return toDto(consentRepository.save(record));
    }

    /**
     * Upload a PDF document and attach to an existing consent record.
     * Validates PDF content type. Replaces any previously uploaded document.
     */
    public ConsentRecordDto uploadConsentDocument(UUID patientBusinessId, UUID consentBusinessId, MultipartFile file) {
        ConsentRecord record = consentRepository.findByBusinessId(consentBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Consent record not found: " + consentBusinessId));

        if (!record.getPatientBusinessId().equals(patientBusinessId)) {
            throw new EntityNotFoundException("Consent record does not belong to patient: " + patientBusinessId);
        }

        String filename = documentStorageService.storePdf(file);
        record.setDocumentPath(filename);
        record.setDocumentFilename(file.getOriginalFilename());
        record.setDocumentContentType("application/pdf");

        return toDto(consentRepository.save(record));
    }

    /**
     * Load a consent document as Resource for HTTP download.
     * Returns Object array: [Resource, originalFilename]
     */
    @Transactional(readOnly = true)
    public Object[] downloadConsentDocument(UUID patientBusinessId, UUID consentBusinessId) {
        ConsentRecord record = consentRepository.findByBusinessId(consentBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Consent record not found: " + consentBusinessId));

        if (!record.getPatientBusinessId().equals(patientBusinessId)) {
            throw new EntityNotFoundException("Consent record does not belong to patient: " + patientBusinessId);
        }

        if (record.getDocumentPath() == null) {
            throw new IllegalStateException("No document uploaded for consent: " + consentBusinessId);
        }

        Resource resource = documentStorageService.loadPdf(record.getDocumentPath());
        String filename = record.getDocumentFilename() != null
            ? record.getDocumentFilename() : record.getDocumentPath();
        return new Object[]{resource, filename};
    }

    /**
     * Revoke a consent. Sets status to REVOKED.
     */
    public ConsentRecordDto revokeConsent(UUID patientBusinessId, UUID consentBusinessId) {
        ConsentRecord record = consentRepository.findByBusinessId(consentBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Consent record not found: " + consentBusinessId));

        if (!record.getPatientBusinessId().equals(patientBusinessId)) {
            throw new EntityNotFoundException("Consent record does not belong to patient: " + patientBusinessId);
        }

        if (record.getStatus() == ConsentStatus.REVOKED) {
            throw new IllegalStateException("Consent is already revoked");
        }

        record.setStatus(ConsentStatus.REVOKED);
        return toDto(consentRepository.save(record));
    }

    /**
     * Get consent alerts for patient profile: MISSING or EXPIRED required consents.
     */
    @Transactional(readOnly = true)
    public List<ConsentAlertDto> getConsentAlerts(UUID patientBusinessId) {
        List<ConsentType> activeTypes = consentRepository.findActiveConsentTypes(
            patientBusinessId, Instant.now());
        List<ConsentAlertDto> alerts = new ArrayList<>();

        for (ConsentType required : ConsentType.values()) {
            if (!required.isRequiredForEncounter()) continue;

            if (!activeTypes.contains(required)) {
                // Check if there's a SIGNED but EXPIRED record
                ConsentRecord existing = consentRepository
                    .findByPatientBusinessIdAndConsentType(patientBusinessId, required)
                    .orElse(null);

                if (existing != null && existing.getStatus() == ConsentStatus.SIGNED && existing.getExpiresAt() != null) {
                    alerts.add(ConsentAlertDto.builder()
                        .consentType(required)
                        .alertType("EXPIRED")
                        .expiredAt(existing.getExpiresAt())
                        .build());
                } else {
                    alerts.add(ConsentAlertDto.builder()
                        .consentType(required)
                        .alertType("MISSING")
                        .build());
                }
            }
        }
        return alerts;
    }

    /**
     * Get missing required consents for a patient (HIPAA + TREATMENT_AUTH + FINANCIAL_RESP).
     */
    @Transactional(readOnly = true)
    public List<ConsentType> getMissingRequiredConsents(UUID patientBusinessId) {
        List<ConsentType> activeTypes = consentRepository.findActiveConsentTypes(
            patientBusinessId, Instant.now());
        return Arrays.stream(ConsentType.values())
            .filter(ConsentType::isRequiredForEncounter)
            .filter(t -> !activeTypes.contains(t))
            .collect(Collectors.toList());
    }

    /**
     * Paginated list of patients missing at least one required consent.
     */
    @Transactional(readOnly = true)
    public Page<PatientMissingConsentDto> getPatientsWithMissingConsents(Pageable pageable) {
        Page<UUID> uuids = consentRepository.findPatientsWithMissingRequiredConsents(pageable);
        return uuids.map(businessId -> {
            List<ConsentType> missing = getMissingRequiredConsents(businessId);
            // Load patient for name/patientId
            return patientRepository.findLatestVersionByBusinessId(businessId)
                .map(p -> PatientMissingConsentDto.builder()
                    .patientBusinessId(businessId)
                    .patientId(p.getPatientId())
                    .fullName(p.getFirstName() + " " + p.getLastName())
                    .missingConsents(missing)
                    .build())
                .orElse(PatientMissingConsentDto.builder()
                    .patientBusinessId(businessId)
                    .missingConsents(missing)
                    .build());
        });
    }

    private Instant computeExpiresAt(ConsentType type, Instant signedAt) {
        return switch (type) {
            case HIPAA_NOTICE -> signedAt.plus(365, ChronoUnit.DAYS);
            case TREATMENT_AUTHORIZATION -> signedAt.plus(5 * 365L, ChronoUnit.DAYS);
            default -> null;
        };
    }

    private String getClientIp() {
        if (httpServletRequest == null) return null;
        String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return httpServletRequest.getRemoteAddr();
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "system";
    }

    private ConsentRecordDto toDto(ConsentRecord r) {
        return ConsentRecordDto.builder()
            .id(r.getId())
            .businessId(r.getBusinessId())
            .patientBusinessId(r.getPatientBusinessId())
            .consentType(r.getConsentType())
            .status(r.getStatus())
            .signedAt(r.getSignedAt())
            .signedBy(r.getSignedBy())
            .expiresAt(r.getExpiresAt())
            .formVersion(r.getFormVersion())
            .notes(r.getNotes())
            .hasDocument(r.getDocumentPath() != null)
            .documentFilename(r.getDocumentFilename())
            .ipAddress(r.getIpAddress())
            .createdAt(r.getCreatedAt())
            .createdBy(r.getCreatedBy())
            .updatedAt(r.getUpdatedAt())
            .build();
    }
}
