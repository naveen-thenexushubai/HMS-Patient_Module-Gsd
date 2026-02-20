package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for data quality dashboard aggregate queries.
 *
 * All queries use nativeQuery = true to query the patients_latest PostgreSQL view.
 * JPQL cannot query views directly — only native SQL works here.
 *
 * The patients_latest view uses DISTINCT ON (business_id) ORDER BY version DESC to return
 * only the latest version row per patient. All queries filter by status = 'ACTIVE'.
 *
 * Performance: COUNT(*) on indexed view columns is O(index scan) — sub-5ms for 50K patients.
 * No pagination needed: dashboard shows totals, not result sets.
 */
@Repository
public interface DataQualityRepository extends JpaRepository<Patient, String> {

    /**
     * Total active patients (latest version per business_id).
     */
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest
        WHERE status = 'ACTIVE'
        """, nativeQuery = true)
    long countTotalActive();

    /**
     * Active patients with incomplete registration (quick-registered, pending completion).
     * is_registration_complete column added by V007 migration.
     */
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest
        WHERE status = 'ACTIVE'
          AND is_registration_complete = false
        """, nativeQuery = true)
    long countIncompleteRegistrations();

    /**
     * Active patients missing an active insurance record.
     * NOT EXISTS subquery: patient has no row in insurance with is_active=true.
     */
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1 FROM insurance i
              WHERE i.patient_business_id = p.business_id
                AND i.is_active = true
          )
        """, nativeQuery = true)
    long countMissingInsurance();

    /**
     * Active patients missing a current photo.
     * NOT EXISTS subquery: patient has no row in patient_photos with is_current=true.
     * patient_photos table added by V006 migration.
     */
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest p
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1 FROM patient_photos ph
              WHERE ph.patient_business_id = p.business_id
                AND ph.is_current = true
          )
        """, nativeQuery = true)
    long countMissingPhotos();

    /**
     * Active patients whose photo ID document has not been verified.
     * photo_id_verified = false means receptionist has not confirmed the ID document.
     * Different from patient_photos: this is about the physical ID document, not a digital photo.
     */
    @Query(value = """
        SELECT COUNT(*) FROM patients_latest
        WHERE status = 'ACTIVE'
          AND photo_id_verified = false
        """, nativeQuery = true)
    long countUnverifiedPhotoIds();
}
