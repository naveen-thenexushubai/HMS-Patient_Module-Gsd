package com.hospital.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddLabResultRequest {

    @NotBlank
    private String testName;

    private String resultValue;
    private String unit;
    private String referenceRange;
    private boolean abnormal;
    private String abnormalFlag;
    private String resultText;
}
