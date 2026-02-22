package com.hospital.patient.api.dto;

import lombok.Data;

@Data
public class UpdateClinicalNoteRequest {

    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
}
