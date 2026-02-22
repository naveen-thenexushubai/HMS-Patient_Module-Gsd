package com.hospital.patient.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_families")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "patient_business_id", nullable = false, unique = true)
    private UUID patientBusinessId;

    @Column(name = "relationship_to_head", nullable = false, length = 50)
    @Builder.Default
    private String relationshipToHead = "MEMBER";

    @Column(name = "is_head", nullable = false)
    @Builder.Default
    private Boolean isHead = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
}
