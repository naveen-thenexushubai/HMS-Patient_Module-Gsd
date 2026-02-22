package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.Patient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for immutable event-sourced Patient entities.
 * Uses PostgreSQL DISTINCT ON for efficient latest-version queries.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {

    /**
     * Query latest version by business ID using PostgreSQL DISTINCT ON.
     * This is the primary method to retrieve a patient's current state.
     */
    @Query(value = """
        SELECT DISTINCT ON (p.business_id) p.*
        FROM patients p
        WHERE p.business_id = :businessId
        ORDER BY p.business_id, p.version DESC, p.created_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Patient> findLatestVersionByBusinessId(@Param("businessId") UUID businessId);

    /**
     * Query latest versions with status filter (for list views).
     * Returns paginated slice for efficient pagination.
     */
    @Query(value = """
        SELECT DISTINCT ON (p.business_id) p.*
        FROM patients p
        WHERE p.status = :status
        ORDER BY p.business_id, p.version DESC, p.created_at DESC
        """, nativeQuery = true)
    Slice<Patient> findLatestVersionsByStatus(@Param("status") String status, Pageable pageable);

    /**
     * Query all versions by business ID (for audit history).
     * Returns versions in descending order (newest first).
     */
    @Query("SELECT p FROM Patient p WHERE p.businessId = :businessId ORDER BY p.version DESC")
    List<Patient> findAllVersionsByBusinessId(@Param("businessId") UUID businessId);

    /**
     * Check if patient ID exists.
     * Useful for validation and duplicate detection.
     */
    boolean existsByPatientId(String patientId);

    /**
     * Find latest versions of active patients by date of birth.
     * Used for duplicate detection during registration.
     */
    @Query(value = """
        SELECT DISTINCT ON (p.business_id) p.*
        FROM patients p
        WHERE p.date_of_birth = :dob AND p.status = 'ACTIVE'
        ORDER BY p.business_id, p.version DESC, p.created_at DESC
        """, nativeQuery = true)
    List<Patient> findLatestVersionsByDateOfBirth(@Param("dob") LocalDate dob);

    /**
     * Find latest version by patient ID.
     * Note: patient_id is unique across all versions, so this returns single result.
     */
    Optional<Patient> findByPatientId(String patientId);

    /**
     * Find the maximum (latest) version number for a patient businessId.
     * Used to compute the next version number when inserting a new patient version.
     * Returns empty Optional if businessId not found.
     */
    @Query("SELECT MAX(p.version) FROM Patient p WHERE p.businessId = :businessId")
    Optional<Long> findMaxVersionByBusinessId(@Param("businessId") UUID businessId);

    /**
     * Find the original version-1 row for a patient by businessId.
     * Used to retrieve registeredAt and registeredBy from the original registration record.
     * Uses native SQL with ORDER BY version ASC LIMIT 1.
     */
    @Query(value = """
        SELECT p.* FROM patients p
        WHERE p.business_id = :businessId
        ORDER BY p.version ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Patient> findFirstVersionByBusinessId(@Param("businessId") UUID businessId);

    /**
     * Find any version of patients by a list of business IDs.
     * Used for batch-loading patient info during insurance verification report.
     * The caller is responsible for deduplicating by version if needed.
     */
    List<Patient> findAllByBusinessIdIn(List<UUID> businessIds);
}
