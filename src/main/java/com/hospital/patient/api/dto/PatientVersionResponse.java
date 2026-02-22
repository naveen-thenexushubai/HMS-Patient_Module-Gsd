package com.hospital.patient.api.dto;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.PatientStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight snapshot of a single patient version row.
 * Returned by GET /api/v1/patients/{businessId}/history
 * Does not include sub-entities (contacts, insurance) — those are shared across versions.
 */
@Data
@Builder
public class PatientVersionResponse {
    private String patientId;
    private UUID businessId;
    private Long version;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phoneNumber;
    private String email;
    private String addressLine1;
    private String city;
    private String state;
    private String zipCode;
    private PatientStatus status;
    private Boolean isRegistrationComplete;
    private Instant recordedAt;   // createdAt of this version row
    private String recordedBy;    // createdBy of this version row
}
