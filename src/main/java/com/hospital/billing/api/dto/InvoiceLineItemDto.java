package com.hospital.billing.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class InvoiceLineItemDto {

    private UUID businessId;
    private UUID invoiceBusinessId;
    private String description;
    private String serviceCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
