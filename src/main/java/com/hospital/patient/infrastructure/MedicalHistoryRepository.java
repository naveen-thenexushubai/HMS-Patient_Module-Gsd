package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Repository for MedicalHistory entities linked to patients via business_id.
 *
 * Queries use patientBusinessId (not Patient FK) to support the event-sourced
 * patient model where patient records are immutable and child tables reference
 * the stable business_id rather than individual record IDs.
 *
 * Usage in PatientService:
 *   Optional<MedicalHistory> history = medicalHistoryRepository
 *       .findLatestByPatientBusinessId(patient.getBusinessId());
 */
@Repository
public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {

    /**
     * Find all medical history records for a patient by business ID.
     * Returns records in natural (insertion) order.
     *
     * @param patientBusinessId The patient's stable business identifier
     * @return List of medical history records for the patient
     */
    List<MedicalHistory> findByPatientBusinessId(UUID patientBusinessId);

    /**
     * Find the most recent medical history record for a patient.
     * Ordered by createdAt DESC to get the latest entry.
     *
     * This supports scenarios where medical history is updated over time,
     * following the same event-sourced pattern as the Patient entity.
     *
     * @param patientBusinessId The patient's stable business identifier
     * @return Optional containing the latest medical history record, or empty if none
     */
    @Query("SELECT m FROM MedicalHistory m WHERE m.patientBusinessId = :patientBusinessId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<MedicalHistory> findLatestByPatientBusinessId(@Param("patientBusinessId") UUID patientBusinessId);

    /**
     * Check if a patient has any medical history records.
     * More efficient than findByPatientBusinessId() when only existence is needed.
     *
     * @param patientBusinessId The patient's stable business identifier
     * @return true if at least one medical history record exists for the patient
     */
    boolean existsByPatientBusinessId(UUID patientBusinessId);

    /**
     * Delete all medical history records for a patient.
     * Used for administrative data cleanup or HIPAA compliance requests.
     *
     * @param patientBusinessId The patient's stable business identifier
     */
    void deleteByPatientBusinessId(UUID patientBusinessId);
}
