package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.PatientRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientRelationshipRepository extends JpaRepository<PatientRelationship, Long> {

    List<PatientRelationship> findByPatientBusinessId(UUID patientBusinessId);

    List<PatientRelationship> findByRelatedPatientBusinessId(UUID relatedPatientBusinessId);

    boolean existsByIdAndPatientBusinessId(Long id, UUID patientBusinessId);

    @Modifying
    @Query("UPDATE PatientRelationship r SET r.patientBusinessId = :targetId WHERE r.patientBusinessId = :sourceId")
    int reassignOwnerToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    @Modifying
    @Query("UPDATE PatientRelationship r SET r.relatedPatientBusinessId = :targetId WHERE r.relatedPatientBusinessId = :sourceId")
    int reassignRelatedToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    long countByPatientBusinessId(UUID patientBusinessId);
}
