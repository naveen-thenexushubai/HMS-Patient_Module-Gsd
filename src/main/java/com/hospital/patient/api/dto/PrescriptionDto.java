package com.hospital.patient.api.dto;

import com.hospital.patient.domain.PrescriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PrescriptionDto {

    private UUID businessId;
    private UUID patientBusinessId;
    private UUID appointmentBusinessId;
    private String medicationName;
    private String genericName;
    private String dosage;
    private String frequency;
    private Integer durationDays;
    private Integer quantityDispensed;
    private Integer refillsRemaining;
    private String instructions;
    private PrescriptionStatus status;
    private String prescribedBy;
    private Instant prescribedAt;
    private LocalDate expiresAt;
    private String discontinueReason;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
