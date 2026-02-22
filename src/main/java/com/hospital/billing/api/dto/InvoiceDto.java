package com.hospital.billing.api.dto;

import com.hospital.billing.domain.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InvoiceDto {

    private UUID businessId;
    private UUID patientBusinessId;
    private UUID appointmentBusinessId;
    private String invoiceNumber;
    private InvoiceStatus status;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal insuranceCovered;
    private BigDecimal patientDue;
    private String currency;
    private LocalDate dueDate;
    private String notes;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private List<InvoiceLineItemDto> lineItems;
    private List<PaymentDto> payments;
}
