package com.hospital.patient.exception;

/**
 * Exception thrown when a patient cannot be found by their identifier.
 * Handled by GlobalExceptionHandler to produce RFC 7807 Problem Details 404 response.
 */
public class PatientNotFoundException extends RuntimeException {

    private final String patientIdentifier;

    public PatientNotFoundException(String patientIdentifier) {
        super("Patient not found: " + patientIdentifier);
        this.patientIdentifier = patientIdentifier;
    }

    public String getPatientIdentifier() {
        return patientIdentifier;
    }
}
