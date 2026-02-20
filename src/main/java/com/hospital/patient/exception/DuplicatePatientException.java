package com.hospital.patient.exception;

import com.hospital.patient.application.DuplicateDetectionService.DuplicateMatch;

import java.util.List;

/**
 * Exception thrown when a potential duplicate patient is detected during registration.
 * Handled by GlobalExceptionHandler to produce RFC 7807 Problem Details 409 response.
 */
public class DuplicatePatientException extends RuntimeException {

    private final List<DuplicateMatch> duplicates;

    public DuplicatePatientException(String message, List<DuplicateMatch> duplicates) {
        super(message);
        this.duplicates = duplicates;
    }

    public List<DuplicateMatch> getDuplicates() {
        return duplicates;
    }
}
