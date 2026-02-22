package com.hospital.patient.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_relationships")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_business_id", nullable = false)
    private UUID patientBusinessId;

    @Column(name = "related_patient_business_id")
    private UUID relatedPatientBusinessId;

    @Column(name = "related_person_name", length = 100)
    private String relatedPersonName;

    @Column(name = "related_person_phone", length = 20)
    private String relatedPersonPhone;

    @Column(name = "related_person_email", length = 255)
    private String relatedPersonEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 50)
    private RelationshipType relationshipType;

    @Column(name = "is_guarantor", nullable = false)
    @Builder.Default
    private Boolean isGuarantor = false;

    @Column(name = "guarantor_account_id", length = 100)
    private String guarantorAccountId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;
}
