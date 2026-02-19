package com.hospital.patient.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable event-sourced patient entity.
 * Patient records are never updated, only new versions are inserted.
 * Use DISTINCT ON queries to get latest version by business_id.
 */
@Entity
@Table(name = "patients")
@Immutable
// @Indexed - Disabled for Phase 1, will be enabled in Phase 3 (Advanced Search)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(generator = "patient_id_generator")
    @GenericGenerator(
        name = "patient_id_generator",
        type = com.hospital.patient.infrastructure.PatientIdGenerator.class
    )
    @Column(name = "patient_id", length = 20)
    private String patientId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private Long version = 1L;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String email;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "photo_id_verified")
    private Boolean photoIdVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientStatus status = PatientStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    // Note: OneToMany relationships are conceptual here since child tables
    // reference business_id (not patient_id). Queries join on business_id.
    // Transient fields for convenience - not mapped by JPA due to non-standard FK.
    @Transient
    @Builder.Default
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<MedicalHistory> medicalHistories = new ArrayList<>();

    /**
     * Calculate patient age based on date of birth.
     */
    @Transient
    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    @PrePersist
    protected void onCreate() {
        if (businessId == null) {
            businessId = UUID.randomUUID();
        }
        if (version == null) {
            version = 1L;
        }
        if (status == null) {
            status = PatientStatus.ACTIVE;
        }
    }
}
