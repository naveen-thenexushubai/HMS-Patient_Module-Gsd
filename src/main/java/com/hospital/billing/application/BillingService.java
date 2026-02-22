package com.hospital.billing.application;

import com.hospital.billing.api.dto.*;
import com.hospital.billing.domain.Invoice;
import com.hospital.billing.domain.InvoiceLineItem;
import com.hospital.billing.domain.InvoiceStatus;
import com.hospital.billing.domain.Payment;
import com.hospital.billing.infrastructure.InvoiceLineItemRepository;
import com.hospital.billing.infrastructure.InvoiceRepository;
import com.hospital.billing.infrastructure.PaymentRepository;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BillingService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineItemRepository lineItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EntityManager entityManager;

    public InvoiceDto createInvoice(UUID patientBusinessId, CreateInvoiceRequest request) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        Long seqVal = (Long) entityManager.createNativeQuery("SELECT nextval('invoice_number_seq')")
            .getSingleResult();
        String invoiceNumber = "INV-" + seqVal;

        BigDecimal totalAmount = request.getLineItems().stream()
            .map(li -> li.getUnitPrice().multiply(BigDecimal.valueOf(li.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal insuranceCovered = request.getInsuranceCovered() != null
            ? request.getInsuranceCovered() : BigDecimal.ZERO;
        BigDecimal patientDue = totalAmount.subtract(insuranceCovered);
        if (patientDue.compareTo(BigDecimal.ZERO) < 0) {
            patientDue = BigDecimal.ZERO;
        }

        Invoice invoice = Invoice.builder()
            .patientBusinessId(patientBusinessId)
            .appointmentBusinessId(request.getAppointmentBusinessId())
            .invoiceNumber(invoiceNumber)
            .totalAmount(totalAmount)
            .insuranceCovered(insuranceCovered)
            .patientDue(patientDue)
            .dueDate(request.getDueDate())
            .notes(request.getNotes())
            .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        List<InvoiceLineItem> lineItems = request.getLineItems().stream()
            .map(li -> InvoiceLineItem.builder()
                .invoiceBusinessId(savedInvoice.getBusinessId())
                .description(li.getDescription())
                .serviceCode(li.getServiceCode())
                .quantity(li.getQuantity())
                .unitPrice(li.getUnitPrice())
                .totalPrice(li.getUnitPrice().multiply(BigDecimal.valueOf(li.getQuantity())))
                .build())
            .collect(Collectors.toList());

        lineItemRepository.saveAll(lineItems);

        return toDto(savedInvoice, lineItems, List.of());
    }

    public InvoiceDto issueInvoice(UUID invoiceBusinessId) {
        Invoice invoice = invoiceRepository.findByBusinessId(invoiceBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceBusinessId));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT invoices can be issued. Current status: " + invoice.getStatus());
        }

        invoice.setStatus(InvoiceStatus.ISSUED);
        Invoice saved = invoiceRepository.save(invoice);

        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceBusinessId(saved.getBusinessId());
        List<Payment> payments = paymentRepository.findByInvoiceBusinessId(saved.getBusinessId());
        return toDto(saved, lineItems, payments);
    }

    public InvoiceDto recordPayment(UUID invoiceBusinessId, RecordPaymentRequest request) {
        Invoice invoice = invoiceRepository.findByBusinessId(invoiceBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceBusinessId));

        if (invoice.getStatus().isTerminal()) {
            throw new IllegalStateException("Cannot add payment to a terminal invoice (status: " + invoice.getStatus() + ")");
        }

        if (!invoice.getStatus().canAddPayment()) {
            throw new IllegalStateException("Invoice must be ISSUED or PARTIALLY_PAID to accept payments. Current status: " + invoice.getStatus());
        }

        Payment payment = Payment.builder()
            .invoiceBusinessId(invoiceBusinessId)
            .patientBusinessId(invoice.getPatientBusinessId())
            .amount(request.getAmount())
            .paymentMethod(request.getPaymentMethod())
            .paymentDate(request.getPaymentDate())
            .referenceNumber(request.getReferenceNumber())
            .notes(request.getNotes())
            .build();

        paymentRepository.save(payment);

        BigDecimal newPaidAmount = invoice.getPaidAmount().add(request.getAmount());
        invoice.setPaidAmount(newPaidAmount);

        if (newPaidAmount.compareTo(invoice.getPatientDue()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        Invoice saved = invoiceRepository.save(invoice);

        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceBusinessId(saved.getBusinessId());
        List<Payment> payments = paymentRepository.findByInvoiceBusinessId(saved.getBusinessId());
        return toDto(saved, lineItems, payments);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getInvoices(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return invoiceRepository.findByPatientBusinessIdOrderByCreatedAtDesc(patientBusinessId)
            .stream()
            .map(inv -> {
                List<InvoiceLineItem> items = lineItemRepository.findByInvoiceBusinessId(inv.getBusinessId());
                List<Payment> pmts = paymentRepository.findByInvoiceBusinessId(inv.getBusinessId());
                return toDto(inv, items, pmts);
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceDto getInvoiceByBusinessId(UUID invoiceBusinessId) {
        Invoice invoice = invoiceRepository.findByBusinessId(invoiceBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceBusinessId));
        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceBusinessId(invoice.getBusinessId());
        List<Payment> payments = paymentRepository.findByInvoiceBusinessId(invoice.getBusinessId());
        return toDto(invoice, lineItems, payments);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "system";
    }

    private InvoiceDto toDto(Invoice inv, List<InvoiceLineItem> lineItems, List<Payment> payments) {
        return InvoiceDto.builder()
            .businessId(inv.getBusinessId())
            .patientBusinessId(inv.getPatientBusinessId())
            .appointmentBusinessId(inv.getAppointmentBusinessId())
            .invoiceNumber(inv.getInvoiceNumber())
            .status(inv.getStatus())
            .totalAmount(inv.getTotalAmount())
            .paidAmount(inv.getPaidAmount())
            .insuranceCovered(inv.getInsuranceCovered())
            .patientDue(inv.getPatientDue())
            .currency(inv.getCurrency())
            .dueDate(inv.getDueDate())
            .notes(inv.getNotes())
            .createdAt(inv.getCreatedAt())
            .createdBy(inv.getCreatedBy())
            .updatedAt(inv.getUpdatedAt())
            .updatedBy(inv.getUpdatedBy())
            .lineItems(lineItems.stream().map(this::toLineItemDto).collect(Collectors.toList()))
            .payments(payments.stream().map(this::toPaymentDto).collect(Collectors.toList()))
            .build();
    }

    private InvoiceLineItemDto toLineItemDto(InvoiceLineItem li) {
        return InvoiceLineItemDto.builder()
            .businessId(li.getBusinessId())
            .invoiceBusinessId(li.getInvoiceBusinessId())
            .description(li.getDescription())
            .serviceCode(li.getServiceCode())
            .quantity(li.getQuantity())
            .unitPrice(li.getUnitPrice())
            .totalPrice(li.getTotalPrice())
            .build();
    }

    private PaymentDto toPaymentDto(Payment p) {
        return PaymentDto.builder()
            .businessId(p.getBusinessId())
            .invoiceBusinessId(p.getInvoiceBusinessId())
            .patientBusinessId(p.getPatientBusinessId())
            .amount(p.getAmount())
            .paymentMethod(p.getPaymentMethod())
            .paymentDate(p.getPaymentDate())
            .referenceNumber(p.getReferenceNumber())
            .notes(p.getNotes())
            .createdAt(p.getCreatedAt())
            .createdBy(p.getCreatedBy())
            .build();
    }
}
