package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.PatientPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for patient photo metadata.
 * Photos are stored on filesystem; this repository manages the metadata records.
 */
@Repository
public interface PatientPhotoRepository extends JpaRepository<PatientPhoto, Long> {

    /**
     * Find the current (active) photo for a patient.
     * Returns empty if patient has no uploaded photo.
     */
    Optional<PatientPhoto> findByPatientBusinessIdAndIsCurrentTrue(UUID patientBusinessId);

    /**
     * Mark all current photos for a patient as not current.
     * Called before inserting a new photo to maintain the single-current-photo invariant.
     *
     * @param patientBusinessId the patient's business UUID
     * @return count of rows updated (0 if no existing photo, 1 normally)
     */
    @Modifying
    @Query("UPDATE PatientPhoto p SET p.isCurrent = false WHERE p.patientBusinessId = :patientBusinessId AND p.isCurrent = true")
    int deactivateCurrentPhotos(@Param("patientBusinessId") UUID patientBusinessId);
}
