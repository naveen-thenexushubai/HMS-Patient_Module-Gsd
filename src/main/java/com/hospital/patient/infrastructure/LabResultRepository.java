package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    List<LabResult> findByLabOrderBusinessId(UUID labOrderBusinessId);

    List<LabResult> findByPatientBusinessIdAndAbnormalTrue(UUID patientBusinessId);

    Optional<LabResult> findByBusinessId(UUID businessId);
}
