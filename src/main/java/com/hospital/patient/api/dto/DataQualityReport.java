package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Data quality dashboard response DTO (SC3 — data quality dashboard).
 *
 * All counts are for ACTIVE patients (latest version from patients_latest view).
 * generatedAt is the timestamp when the report was generated (real-time, not cached).
 */
@Data
@Builder
public class DataQualityReport {

    /** Total number of ACTIVE patients (latest version per businessId). */
    private long totalActivePatients;

    /** Active patients where isRegistrationComplete = false (quick-registered, pending completion). */
    private long incompleteRegistrations;

    /** Active patients with no active insurance record in the insurance table. */
    private long missingInsurance;

    /** Active patients with no current photo (is_current=true) in the patient_photos table. */
    private long missingPhoto;

    /** Active patients where photoIdVerified = false (photo ID document not confirmed). */
    private long unverifiedPhotoIds;

    /** Timestamp when this report was generated. */
    private Instant generatedAt;
}
