package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Repository for EmergencyContact entities linked to patients via business_id.
 *
 * Queries use patientBusinessId (not Patient FK) to support the event-sourced
 * patient model where patient records are immutable and child tables reference
 * the stable business_id rather than individual record IDs.
 *
 * Usage in PatientService:
 *   List contacts = emergencyContactRepository
 *       .findByPatientBusinessIdOrderByIsPrimaryDesc(patient.getBusinessId());
 */
@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    /**
     * Find all emergency contacts for a patient by business ID.
     * Returns contacts in natural (insertion) order.
     *
     * @param patientBusinessId The patient's stable business identifier
     * @return List of emergency contacts for the patient
     */
    List<EmergencyContact> findByPatientBusinessId(UUID patientBusinessId);

    /**
     * Find all emergency contacts for a patient, ordered by isPrimary DESC.
     * Primary contacts appear first in the list.
     *
     * @param patientBusinessId The patient's stable business identifier
     * @return List of emergency contacts ordered by primary status (primary first)
     */
    List<EmergencyContact> findByPatientBusinessIdOrderByIsPrimaryDesc(UUID patientBusinessId);

    /**
     * Check if a patient has any emergency contacts.
     * More efficient than findByPatientBusinessId() when only existence is needed.
     *
     * @param patientBusinessId The patient's stable business identifier
     * @return true if at least one emergency contact exists for the patient
     */
    boolean existsByPatientBusinessId(UUID patientBusinessId);

    /**
     * Delete all emergency contacts for a patient.
     * Used when re-registering a patient or administrative data cleanup.
     *
     * @param patientBusinessId The patient's stable business identifier
     */
    void deleteByPatientBusinessId(UUID patientBusinessId);
}
