package com.hospital.patient.api.dto;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.PatientStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight DTO for patient search results.
 * Contains only essential information needed for list views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSummaryResponse {
    private UUID businessId;
    private String patientId;
    private String fullName;
    private int age;
    private Gender gender;
    private String phoneNumber;
    private PatientStatus status;
}
