package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.PatientPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientPhotoRepository extends JpaRepository<PatientPhoto, Long> {

    Optional<PatientPhoto> findByPatientBusinessIdAndIsCurrentTrue(UUID patientBusinessId);

    @Modifying
    @Query("UPDATE PatientPhoto p SET p.isCurrent = false WHERE p.patientBusinessId = :patientBusinessId AND p.isCurrent = true")
    int deactivateCurrentPhotos(@Param("patientBusinessId") UUID patientBusinessId);

    @Modifying
    @Query("UPDATE PatientPhoto p SET p.patientBusinessId = :targetId WHERE p.patientBusinessId = :sourceId")
    int reassignToPatient(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    long countByPatientBusinessId(UUID patientBusinessId);
}
