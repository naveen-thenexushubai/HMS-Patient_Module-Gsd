package com.hospital.patient.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for patient photo metadata stored in the patient_photos table.
 *
 * Binary photo files are stored on the local filesystem (app.storage.photos-dir).
 * This entity stores only metadata: UUID-based filename (for filesystem lookup),
 * content type, file size, and audit fields.
 *
 * is_current: only one row per patient should have is_current=true at a time.
 * When a new photo is uploaded, all existing rows for that patient are set to is_current=false
 * before the new row is inserted.
 *
 * No @Immutable — unlike patients, photo records are mutable (is_current flag changes).
 * No FK to patients table — event-sourced patients repeat business_id across versions.
 */
@Entity
@Table(name = "patient_photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_business_id", nullable = false)
    private UUID patientBusinessId;

    /** UUID-based filename for filesystem storage. Never the original upload filename. */
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /** MIME type: "image/jpeg" or "image/png" */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private Instant uploadedAt = Instant.now();

    @Column(name = "uploaded_by", nullable = false, length = 255)
    private String uploadedBy;

    /** True for the current/active photo. False for superseded photos. */
    @Column(name = "is_current")
    @Builder.Default
    private Boolean isCurrent = true;
}
