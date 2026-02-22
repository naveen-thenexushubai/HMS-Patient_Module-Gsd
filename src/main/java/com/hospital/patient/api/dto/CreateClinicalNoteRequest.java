package com.hospital.patient.api.dto;

import com.hospital.patient.domain.NoteType;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateClinicalNoteRequest {

    private NoteType noteType = NoteType.SOAP;
    private UUID appointmentBusinessId;
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
}
