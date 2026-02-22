package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.Prescription;
import com.hospital.patient.domain.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByPatientBusinessIdOrderByPrescribedAtDesc(UUID patientBusinessId);

    List<Prescription> findByPatientBusinessIdAndStatus(UUID patientBusinessId, PrescriptionStatus status);

    Optional<Prescription> findByBusinessId(UUID businessId);
}
