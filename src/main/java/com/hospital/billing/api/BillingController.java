package com.hospital.billing.api;

import com.hospital.billing.api.dto.CreateInvoiceRequest;
import com.hospital.billing.api.dto.InvoiceDto;
import com.hospital.billing.api.dto.RecordPaymentRequest;
import com.hospital.billing.application.BillingService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @PostMapping("/patients/{businessId}/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Audited(action = "CREATE", resourceType = "INVOICE")
    public ResponseEntity<InvoiceDto> createInvoice(
        @PathVariable UUID businessId,
        @Valid @RequestBody CreateInvoiceRequest request
    ) {
        InvoiceDto result = billingService.createInvoice(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/patients/{businessId}/invoices")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    @Audited(action = "READ", resourceType = "INVOICE")
    public ResponseEntity<List<InvoiceDto>> getInvoices(@PathVariable UUID businessId) {
        return ResponseEntity.ok(billingService.getInvoices(businessId));
    }

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceDto> getInvoice(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(billingService.getInvoiceByBusinessId(invoiceId));
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "UPDATE", resourceType = "INVOICE")
    public ResponseEntity<InvoiceDto> issueInvoice(@PathVariable UUID invoiceId) {
        InvoiceDto result = billingService.issueInvoice(invoiceId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Audited(action = "CREATE", resourceType = "PAYMENT")
    public ResponseEntity<InvoiceDto> recordPayment(
        @PathVariable UUID invoiceId,
        @Valid @RequestBody RecordPaymentRequest request
    ) {
        InvoiceDto result = billingService.recordPayment(invoiceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
