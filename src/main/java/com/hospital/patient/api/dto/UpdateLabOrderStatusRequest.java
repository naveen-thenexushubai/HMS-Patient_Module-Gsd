package com.hospital.patient.api.dto;

import com.hospital.patient.domain.LabOrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateLabOrderStatusRequest {

    @NotNull
    private LabOrderStatus status;
}
