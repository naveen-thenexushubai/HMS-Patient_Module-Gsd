package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.PatientFamily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientFamilyRepository extends JpaRepository<PatientFamily, Long> {

    List<PatientFamily> findByHouseholdId(UUID householdId);

    Optional<PatientFamily> findByPatientBusinessId(UUID patientBusinessId);

    boolean existsByPatientBusinessId(UUID patientBusinessId);

    @Modifying
    @Query("UPDATE PatientFamily f SET f.patientBusinessId = :targetId WHERE f.patientBusinessId = :sourceId")
    int reassignToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    long countByPatientBusinessId(UUID patientBusinessId);
}
