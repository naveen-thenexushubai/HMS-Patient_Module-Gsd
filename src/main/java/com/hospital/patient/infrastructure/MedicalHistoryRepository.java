package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {

    List<MedicalHistory> findByPatientBusinessId(UUID patientBusinessId);

    @Query("SELECT m FROM MedicalHistory m WHERE m.patientBusinessId = :patientBusinessId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<MedicalHistory> findLatestByPatientBusinessId(@Param("patientBusinessId") UUID patientBusinessId);

    boolean existsByPatientBusinessId(UUID patientBusinessId);

    void deleteByPatientBusinessId(UUID patientBusinessId);

    @Modifying
    @Query("UPDATE MedicalHistory m SET m.patientBusinessId = :targetId WHERE m.patientBusinessId = :sourceId")
    int reassignToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    long countByPatientBusinessId(UUID patientBusinessId);
}
