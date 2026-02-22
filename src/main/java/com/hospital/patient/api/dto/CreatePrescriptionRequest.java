package com.hospital.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreatePrescriptionRequest {

    private UUID appointmentBusinessId;

    @NotBlank
    private String medicationName;

    private String genericName;

    @NotBlank
    private String dosage;

    @NotBlank
    private String frequency;

    private Integer durationDays;
    private Integer quantityDispensed;
    private Integer refillsRemaining;
    private String instructions;
    private LocalDate expiresAt;
}
