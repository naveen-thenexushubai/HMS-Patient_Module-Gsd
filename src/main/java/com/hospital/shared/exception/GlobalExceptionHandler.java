package com.hospital.shared.exception;

import com.hospital.patient.application.DuplicateDetectionService.DuplicateMatch;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.exception.DuplicatePatientException;
import com.hospital.patient.exception.PatientNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler implementing RFC 7807 Problem Details for all API errors.
 *
 * Handles:
 * - Bean validation errors (400 Bad Request) with field-level details
 * - PatientNotFoundException (404 Not Found)
 * - DuplicatePatientException (409 Conflict) with potential duplicate details
 * - AccessDeniedException (403 Forbidden)
 * - Generic exceptions (500 Internal Server Error)
 *
 * All responses conform to RFC 7807 Problem Details format:
 * type, title, status, detail, and optional extension properties.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_BASE_URL = "https://api.hospital.com/problems";

    /**
     * Handle validation errors (JSR-380 Bean Validation).
     * Returns RFC 7807 Problem Details with field-level errors map.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed for patient request"
        );
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/validation-error"));

        // Collect field-level errors grouped by field name
        Map<String, List<String>> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String field = error.getField();
            String message = error.getDefaultMessage();
            fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
        }

        problemDetail.setProperty("fieldErrors", fieldErrors);
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handle patient not found errors.
     * Returns 404 with RFC 7807 Problem Details including the patient identifier.
     */
    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePatientNotFound(
        PatientNotFoundException ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problemDetail.setTitle("Patient Not Found");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/patient-not-found"));
        problemDetail.setProperty("patientIdentifier", ex.getPatientIdentifier());
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Handle duplicate patient detection errors.
     * Returns 409 with RFC 7807 Problem Details including potential duplicate matches.
     */
    @ExceptionHandler(DuplicatePatientException.class)
    public ResponseEntity<ProblemDetail> handleDuplicatePatient(
        DuplicatePatientException ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        problemDetail.setTitle("Duplicate Patient Detected");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/duplicate-patient"));

        // Convert duplicates to a serializable list of maps
        List<Map<String, Object>> duplicatesInfo = ex.getDuplicates().stream()
            .map(match -> {
                Map<String, Object> info = new HashMap<>();
                Patient p = match.getPatient();
                info.put("patientId", p.getPatientId());
                info.put("fullName", p.getFirstName() + " " + p.getLastName());
                info.put("dateOfBirth", p.getDateOfBirth().toString());
                info.put("phoneNumber", p.getPhoneNumber());
                info.put("similarityScore", (int) (match.getScore() * 100));
                return info;
            })
            .collect(Collectors.toList());

        problemDetail.setProperty("potentialDuplicates", duplicatesInfo);
        problemDetail.setProperty("requiresManualOverride", true);
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    /**
     * Handle Spring Security access denied errors.
     * Returns 403 with RFC 7807 Problem Details.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
        AccessDeniedException ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Access denied: " + ex.getMessage()
        );
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/access-denied"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    /**
     * Handle concurrent patient version conflicts.
     * The patients table has UNIQUE(business_id, version) constraint (uk_patients_business_version).
     * When two simultaneous updates increment to the same version number, the second insert
     * violates this constraint. Return 409 Conflict with a human-readable message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
        DataIntegrityViolationException ex,
        WebRequest request
    ) {
        if (ex.getMessage() != null && ex.getMessage().contains("uk_patients_business_version")) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Patient was updated concurrently. Please reload the patient profile and try again."
            );
            problemDetail.setTitle("Concurrent Update Conflict");
            problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/concurrent-update"));
            problemDetail.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "Data integrity constraint violated"
        );
        problemDetail.setTitle("Data Conflict");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/conflict"));
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    /**
     * Handle JPA entity not found errors (e.g., no active insurance found).
     * Returns 404 with RFC 7807 Problem Details.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEntityNotFound(
        EntityNotFoundException ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Handle file upload size exceeded errors (SC2 - photo upload size limit).
     * MaxUploadSizeExceededException is thrown by DispatcherServlet filter chain BEFORE
     * the controller method is reached when upload exceeds spring.servlet.multipart.max-file-size.
     * Without this handler, the exception becomes a 500 error. RFC 7807 400 is correct response.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(
        MaxUploadSizeExceededException ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Uploaded file exceeds the maximum allowed size of 5MB. Please reduce the file size and try again."
        );
        problemDetail.setTitle("File Too Large");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/file-too-large"));
        problemDetail.setProperty("maxFileSizeMB", 5);
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handle invalid file arguments (invalid content type, non-image file, path traversal).
     * Thrown by FileStorageService.store() with descriptive messages.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
        IllegalArgumentException ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        problemDetail.setTitle("Invalid Request");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/invalid-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handle all other uncaught exceptions.
     * Returns 500 with RFC 7807 Problem Details (without exposing internal details).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
        Exception ex,
        WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create(PROBLEM_BASE_URL + "/internal-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
