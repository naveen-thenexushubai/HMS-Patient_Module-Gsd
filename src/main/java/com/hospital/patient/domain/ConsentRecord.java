package com.hospital.patient.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(name = "patient_business_id", nullable = false)
    private UUID patientBusinessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConsentStatus status = ConsentStatus.PENDING;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by", length = 255)
    private String signedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "form_version", nullable = false, length = 20)
    @Builder.Default
    private String formVersion = "1.0";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Column(name = "document_filename", length = 255)
    private String documentFilename;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (businessId == null) {
            businessId = UUID.randomUUID();
        }
        if (status == null) {
            status = ConsentStatus.PENDING;
        }
        if (formVersion == null) {
            formVersion = "1.0";
        }
    }
}
