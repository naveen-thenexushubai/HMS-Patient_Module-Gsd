package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.VitalSigns;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {

    Page<VitalSigns> findByPatientBusinessIdOrderByRecordedAtDesc(UUID patientBusinessId, Pageable pageable);

    Optional<VitalSigns> findTopByPatientBusinessIdOrderByRecordedAtDesc(UUID patientBusinessId);

    Optional<VitalSigns> findByBusinessId(UUID businessId);
}
