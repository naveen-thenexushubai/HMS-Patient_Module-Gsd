package com.hospital.patient.api.dto;

import com.hospital.patient.domain.NoteType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ClinicalNoteDto {

    private UUID businessId;
    private UUID patientBusinessId;
    private UUID appointmentBusinessId;
    private NoteType noteType;
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
    private boolean finalized;
    private Instant finalizedAt;
    private String finalizedBy;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
