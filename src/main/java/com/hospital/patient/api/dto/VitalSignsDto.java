package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VitalSignsDto {

    private UUID businessId;
    private UUID patientBusinessId;
    private UUID appointmentBusinessId;
    private Instant recordedAt;
    private BigDecimal temperature;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer heartRate;
    private Integer respiratoryRate;
    private BigDecimal oxygenSaturation;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal bmi;
    private String notes;
    private Instant createdAt;
    private String createdBy;
}
