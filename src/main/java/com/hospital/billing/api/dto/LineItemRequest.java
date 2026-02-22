package com.hospital.billing.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LineItemRequest {

    @NotBlank
    private String description;

    private String serviceCode;

    @Min(1)
    private int quantity = 1;

    @DecimalMin("0.00")
    private BigDecimal unitPrice;
}
