package com.hospital.billing.infrastructure;

import com.hospital.billing.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByPatientBusinessIdOrderByCreatedAtDesc(UUID patientBusinessId);

    Optional<Invoice> findByBusinessId(UUID businessId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
