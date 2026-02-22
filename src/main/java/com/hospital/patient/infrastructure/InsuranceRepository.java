package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.Insurance;
import com.hospital.patient.domain.InsuranceVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, Long> {

    List<Insurance> findByPatientBusinessId(UUID patientBusinessId);

    Optional<Insurance> findByPatientBusinessIdAndIsActiveTrue(UUID patientBusinessId);

    @Modifying
    @Query("UPDATE Insurance i SET i.patientBusinessId = :targetId WHERE i.patientBusinessId = :sourceId")
    int reassignToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    long countByPatientBusinessId(UUID patientBusinessId);

    List<Insurance> findByIsActiveTrue();

    Page<Insurance> findByVerificationStatusAndIsActiveTrue(
        InsuranceVerificationStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE Insurance i SET i.verificationStatus = :status, i.lastVerifiedAt = :verifiedAt WHERE i.id = :id")
    void updateVerificationStatus(
        @Param("id") Long id,
        @Param("status") InsuranceVerificationStatus status,
        @Param("verifiedAt") Instant verifiedAt);
}
