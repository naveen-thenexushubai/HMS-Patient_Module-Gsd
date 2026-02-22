package com.hospital.patient.application;

import com.hospital.patient.api.dto.InsuranceVerificationReportItem;
import com.hospital.patient.api.dto.InsuranceVerificationSummary;
import com.hospital.patient.domain.Insurance;
import com.hospital.patient.domain.InsuranceVerificationStatus;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.infrastructure.InsuranceRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for bulk insurance verification.
 * Verifies all active insurance records and updates their verification_status.
 *
 * Verification rules:
 * - VERIFIED: isActive=true, providerName not blank, policyNumber not blank,
 *             coverageType not null, record updated within 365 days
 * - INCOMPLETE: isActive=true, missing providerName or policyNumber or coverageType
 * - STALE: isActive=true, all fields present, but record older than 365 days
 * - PENDING: default for newly created records
 */
@Service
public class InsuranceVerificationService {

    private static final int STALE_DAYS = 365;

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Verify all active insurance records and update their verification status.
     * Called by nightly job and on-demand trigger.
     *
     * @return Summary of verification results
     */
    @Transactional
    public InsuranceVerificationSummary verifyAll() {
        List<Insurance> activeInsurances = insuranceRepository.findByIsActiveTrue();
        Instant now = Instant.now();
        Instant staleThreshold = now.minus(STALE_DAYS, ChronoUnit.DAYS);

        int verifiedCount = 0, incompleteCount = 0, staleCount = 0, pendingCount = 0;

        for (Insurance insurance : activeInsurances) {
            InsuranceVerificationStatus newStatus = computeStatus(insurance, staleThreshold);

            // Only update if status changed (avoids unnecessary writes)
            if (newStatus != insurance.getVerificationStatus()) {
                insuranceRepository.updateVerificationStatus(insurance.getId(), newStatus, now);
            }

            switch (newStatus) {
                case VERIFIED -> verifiedCount++;
                case INCOMPLETE -> incompleteCount++;
                case STALE -> staleCount++;
                case PENDING -> pendingCount++;
            }
        }

        return InsuranceVerificationSummary.builder()
            .processedCount(activeInsurances.size())
            .verifiedCount(verifiedCount)
            .incompleteCount(incompleteCount)
            .staleCount(staleCount)
            .pendingCount(pendingCount)
            .build();
    }

    /**
     * Get a paginated report of insurance records not in VERIFIED status.
     * Enriches each record with patient information.
     *
     * @param status Filter by verification status (null = all non-verified, defaults to INCOMPLETE)
     * @param pageable Pagination
     * @return Page of verification report items
     */
    @Transactional(readOnly = true)
    public Page<InsuranceVerificationReportItem> getVerificationReport(
        InsuranceVerificationStatus status,
        Pageable pageable
    ) {
        // Default: show INCOMPLETE if no status filter given
        InsuranceVerificationStatus filterStatus = status != null ? status : InsuranceVerificationStatus.INCOMPLETE;

        Page<Insurance> insurancePage = insuranceRepository
            .findByVerificationStatusAndIsActiveTrue(filterStatus, pageable);

        // Batch-load patient info for all records in this page
        List<UUID> patientIds = insurancePage.getContent().stream()
            .map(Insurance::getPatientBusinessId)
            .distinct()
            .collect(Collectors.toList());

        Map<UUID, Patient> patientMap = patientIds.isEmpty()
            ? Map.of()
            : patientRepository.findAllByBusinessIdIn(patientIds).stream()
                .collect(Collectors.toMap(Patient::getBusinessId, Function.identity(),
                    (a, b) -> a.getVersion() >= b.getVersion() ? a : b));

        return insurancePage.map(ins -> {
            Patient patient = patientMap.get(ins.getPatientBusinessId());
            return InsuranceVerificationReportItem.builder()
                .insuranceId(ins.getId())
                .patientBusinessId(ins.getPatientBusinessId())
                .patientId(patient != null ? patient.getPatientId() : null)
                .patientFullName(patient != null
                    ? patient.getFirstName() + " " + patient.getLastName() : null)
                .providerName(ins.getProviderName())
                .coverageType(ins.getCoverageType())
                .verificationStatus(ins.getVerificationStatus())
                .lastVerifiedAt(ins.getLastVerifiedAt())
                .createdAt(ins.getCreatedAt())
                .updatedAt(ins.getUpdatedAt())
                .build();
        });
    }

    private InsuranceVerificationStatus computeStatus(Insurance ins, Instant staleThreshold) {
        boolean hasRequiredFields = ins.getProviderName() != null && !ins.getProviderName().isBlank()
            && ins.getPolicyNumber() != null && !ins.getPolicyNumber().isBlank()
            && ins.getCoverageType() != null;

        if (!hasRequiredFields) {
            return InsuranceVerificationStatus.INCOMPLETE;
        }

        // Check staleness based on updatedAt (falls back to createdAt if never updated)
        Instant referenceDate = ins.getUpdatedAt() != null ? ins.getUpdatedAt() : ins.getCreatedAt();
        if (referenceDate != null && referenceDate.isBefore(staleThreshold)) {
            return InsuranceVerificationStatus.STALE;
        }

        return InsuranceVerificationStatus.VERIFIED;
    }
}
