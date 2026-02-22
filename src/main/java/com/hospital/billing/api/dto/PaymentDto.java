package com.hospital.billing.api.dto;

import com.hospital.billing.domain.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PaymentDto {

    private UUID businessId;
    private UUID invoiceBusinessId;
    private UUID patientBusinessId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private LocalDate paymentDate;
    private String referenceNumber;
    private String notes;
    private Instant createdAt;
    private String createdBy;
}
