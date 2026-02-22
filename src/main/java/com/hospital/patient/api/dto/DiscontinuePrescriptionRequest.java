package com.hospital.patient.api.dto;

import lombok.Data;

@Data
public class DiscontinuePrescriptionRequest {

    private String discontinueReason;
}
