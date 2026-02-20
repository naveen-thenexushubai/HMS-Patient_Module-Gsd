package com.hospital.patient.application;

import com.hospital.patient.api.dto.InsuranceDto;
import com.hospital.patient.domain.Insurance;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.InsuranceRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class InsuranceService {

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Create insurance for a patient (INS-01, INS-03).
     * If an active insurance record already exists, mark it inactive and create a new one.
     * This preserves history while maintaining the single-active-record invariant.
     *
     * @throws PatientNotFoundException if patient businessId not found
     */
    public InsuranceDto createInsurance(UUID businessId, InsuranceDto dto) {
        // Verify patient exists
        patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        // Deactivate existing active insurance (if present) to maintain single-active-record
        insuranceRepository.findByPatientBusinessIdAndIsActiveTrue(businessId)
            .ifPresent(existing -> {
                existing.setIsActive(false);
                insuranceRepository.save(existing);
            });

        Insurance insurance = Insurance.builder()
            .patientBusinessId(businessId)
            .providerName(dto.getProviderName())
            .policyNumber(dto.getPolicyNumber())    // stored encrypted by SensitiveDataConverter
            .groupNumber(dto.getGroupNumber())       // stored encrypted by SensitiveDataConverter
            .coverageType(dto.getCoverageType())
            .isActive(true)
            .build();

        Insurance saved = insuranceRepository.save(insurance);
        return toDto(saved);
    }

    /**
     * Update existing active insurance for a patient (INS-05).
     * Modifies the active insurance record in-place (Insurance is a mutable table).
     * @LastModifiedDate and @LastModifiedBy are set automatically by AuditingEntityListener.
     *
     * @throws PatientNotFoundException if patient not found
     * @throws jakarta.persistence.EntityNotFoundException if no active insurance exists
     */
    public InsuranceDto updateInsurance(UUID businessId, InsuranceDto dto) {
        patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        Insurance insurance = insuranceRepository.findByPatientBusinessIdAndIsActiveTrue(businessId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "No active insurance found for patient: " + businessId));

        insurance.setProviderName(dto.getProviderName());
        insurance.setPolicyNumber(dto.getPolicyNumber());   // re-encrypted by SensitiveDataConverter
        insurance.setGroupNumber(dto.getGroupNumber());     // re-encrypted by SensitiveDataConverter
        insurance.setCoverageType(dto.getCoverageType());
        // isActive stays true; updatedAt/updatedBy set by @LastModifiedDate/@LastModifiedBy automatically

        Insurance saved = insuranceRepository.save(insurance);
        return toDto(saved);
    }

    /**
     * Get active insurance for a patient (INS-04).
     * Returns Optional.empty() if no active insurance exists (used by patient profile GET).
     */
    @Transactional(readOnly = true)
    public Optional<InsuranceDto> getActiveInsurance(UUID businessId) {
        return insuranceRepository.findByPatientBusinessIdAndIsActiveTrue(businessId)
            .map(this::toDto);
    }

    private InsuranceDto toDto(Insurance insurance) {
        return InsuranceDto.builder()
            .id(insurance.getId())
            .providerName(insurance.getProviderName())
            .policyNumber(insurance.getPolicyNumber())   // decrypted by SensitiveDataConverter on read
            .groupNumber(insurance.getGroupNumber())     // decrypted by SensitiveDataConverter on read
            .coverageType(insurance.getCoverageType())
            .isActive(insurance.getIsActive())
            .createdAt(insurance.getCreatedAt())
            .createdBy(insurance.getCreatedBy())
            .updatedAt(insurance.getUpdatedAt())
            .updatedBy(insurance.getUpdatedBy())
            .build();
    }
}
