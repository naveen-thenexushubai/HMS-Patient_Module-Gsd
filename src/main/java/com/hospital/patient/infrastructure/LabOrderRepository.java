package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    List<LabOrder> findByPatientBusinessIdOrderByCreatedAtDesc(UUID patientBusinessId);

    Optional<LabOrder> findByBusinessId(UUID businessId);
}
