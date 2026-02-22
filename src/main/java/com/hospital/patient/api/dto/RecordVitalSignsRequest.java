package com.hospital.patient.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RecordVitalSignsRequest {

    private UUID appointmentBusinessId;

    @DecimalMin("30.0") @DecimalMax("45.0")
    private BigDecimal temperature;

    @Min(50) @Max(300)
    private Integer systolicBp;

    @Min(30) @Max(200)
    private Integer diastolicBp;

    @Min(20) @Max(300)
    private Integer heartRate;

    @Min(1) @Max(60)
    private Integer respiratoryRate;

    @DecimalMin("0.0") @DecimalMax("100.0")
    private BigDecimal oxygenSaturation;

    @DecimalMin("1.0") @DecimalMax("500.0")
    private BigDecimal weightKg;

    @DecimalMin("1.0") @DecimalMax("300.0")
    private BigDecimal heightCm;

    private String notes;
}
