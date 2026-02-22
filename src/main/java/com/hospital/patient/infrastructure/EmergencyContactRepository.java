package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByPatientBusinessId(UUID patientBusinessId);

    List<EmergencyContact> findByPatientBusinessIdOrderByIsPrimaryDesc(UUID patientBusinessId);

    boolean existsByPatientBusinessId(UUID patientBusinessId);

    void deleteByPatientBusinessId(UUID patientBusinessId);

    @Modifying
    @Query("UPDATE EmergencyContact e SET e.patientBusinessId = :targetId WHERE e.patientBusinessId = :sourceId")
    int reassignToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    long countByPatientBusinessId(UUID patientBusinessId);
}
