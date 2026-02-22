package com.hospital.patient.api.dto;

import com.hospital.patient.domain.LabOrderPriority;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateLabOrderRequest {

    @NotBlank
    private String orderName;

    private LabOrderPriority priority = LabOrderPriority.ROUTINE;
    private String notes;
    private UUID appointmentBusinessId;
}
