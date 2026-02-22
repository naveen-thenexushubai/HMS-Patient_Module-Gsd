package com.hospital.billing.api.dto;

import com.hospital.billing.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecordPaymentRequest {

    @DecimalMin("0.01")
    @NotNull
    private BigDecimal amount;

    @NotNull
    private PaymentMethod paymentMethod;

    @NotNull
    private LocalDate paymentDate;

    private String referenceNumber;
    private String notes;
}
