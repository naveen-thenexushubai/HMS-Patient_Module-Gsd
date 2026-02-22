package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.ConsentRecord;
import com.hospital.patient.domain.ConsentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<ConsentRecord, Long> {

    List<ConsentRecord> findByPatientBusinessIdOrderByConsentTypeAsc(UUID patientBusinessId);

    Optional<ConsentRecord> findByBusinessId(UUID businessId);

    Optional<ConsentRecord> findByPatientBusinessIdAndConsentType(
        UUID patientBusinessId, ConsentType consentType
    );

    @Query("""
        SELECT c.consentType FROM ConsentRecord c
        WHERE c.patientBusinessId = :patientBusinessId
          AND c.status = com.hospital.patient.domain.ConsentStatus.SIGNED
          AND (c.expiresAt IS NULL OR c.expiresAt > :now)
        """)
    List<ConsentType> findActiveConsentTypes(
        @Param("patientBusinessId") UUID patientBusinessId,
        @Param("now") Instant now
    );

    @Query(value = """
        SELECT DISTINCT p.business_id
        FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
            SELECT 1 FROM consent_records c
            WHERE c.patient_business_id = p.business_id
              AND c.consent_type = 'HIPAA_NOTICE'
              AND c.status = 'SIGNED'
              AND (c.expires_at IS NULL OR c.expires_at > NOW())
          )
        UNION
        SELECT DISTINCT p.business_id
        FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
            SELECT 1 FROM consent_records c
            WHERE c.patient_business_id = p.business_id
              AND c.consent_type = 'TREATMENT_AUTHORIZATION'
              AND c.status = 'SIGNED'
              AND (c.expires_at IS NULL OR c.expires_at > NOW())
          )
        UNION
        SELECT DISTINCT p.business_id
        FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
            SELECT 1 FROM consent_records c
            WHERE c.patient_business_id = p.business_id
              AND c.consent_type = 'FINANCIAL_RESPONSIBILITY'
              AND c.status = 'SIGNED'
              AND (c.expires_at IS NULL OR c.expires_at > NOW())
          )
        ORDER BY business_id
        """,
        countQuery = """
        SELECT COUNT(DISTINCT subq.business_id) FROM (
          SELECT p.business_id
          FROM patients_latest p
          WHERE p.status = 'ACTIVE'
            AND NOT EXISTS (
              SELECT 1 FROM consent_records c
              WHERE c.patient_business_id = p.business_id
                AND c.consent_type IN ('HIPAA_NOTICE','TREATMENT_AUTHORIZATION','FINANCIAL_RESPONSIBILITY')
                AND c.status = 'SIGNED'
                AND (c.expires_at IS NULL OR c.expires_at > NOW())
            )
        ) subq
        """,
        nativeQuery = true)
    Page<UUID> findPatientsWithMissingRequiredConsents(Pageable pageable);
}
