package com.hospital.billing.domain;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    WRITTEN_OFF,
    CANCELLED;

    public boolean isTerminal() {
        return this == PAID || this == WRITTEN_OFF || this == CANCELLED;
    }

    public boolean canAddPayment() {
        return this == ISSUED || this == PARTIALLY_PAID || this == OVERDUE;
    }
}
