package com.hospital.patient.domain;

/**
 * Patient status enum for event-sourced patient records.
 * Only latest version of patient should be considered for status checks.
 */
public enum PatientStatus {
    ACTIVE,
    INACTIVE
}
