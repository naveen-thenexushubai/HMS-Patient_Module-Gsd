package com.hospital.patient.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Details of a single potential duplicate patient match.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateMatchDto {

    /**
     * Patient ID of the existing patient record.
     */
    private String patientId;

    /**
     * Full name of the existing patient.
     */
    private String fullName;

    /**
     * Date of birth of the existing patient.
     */
    private LocalDate dateOfBirth;

    /**
     * Phone number of the existing patient.
     */
    private String phoneNumber;

    /**
     * Email of the existing patient.
     */
    private String email;

    /**
     * Similarity score as percentage (0-100).
     * 85-89: Warning, 90+: Block
     */
    private int similarityScore;
}
