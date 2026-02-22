package com.hospital.billing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateInvoiceRequest {

    @NotEmpty
    @Valid
    private List<LineItemRequest> lineItems;

    private BigDecimal insuranceCovered;
    private LocalDate dueDate;
    private String notes;
    private UUID appointmentBusinessId;
}
