package com.hospital.patient.domain;

/**
 * Verification status for an insurance record.
 *
 * PENDING  — freshly created, not yet verified by the nightly job
 * VERIFIED — all required fields present and record is within 365 days old
 * INCOMPLETE — missing providerName, policyNumber, or coverageType
 * STALE — all fields present but record is older than 365 days
 */
public enum InsuranceVerificationStatus {
    PENDING,
    VERIFIED,
    INCOMPLETE,
    STALE
}
