package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {
    List<MedicalHistory> findByPatientBusinessId(UUID patientBusinessId);
}
