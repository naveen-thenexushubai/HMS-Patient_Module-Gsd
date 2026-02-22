package com.hospital.patient.api.dto;

import com.hospital.patient.domain.LabOrderPriority;
import com.hospital.patient.domain.LabOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LabOrderDto {

    private UUID businessId;
    private UUID patientBusinessId;
    private UUID appointmentBusinessId;
    private String orderName;
    private String orderedBy;
    private LabOrderStatus status;
    private LabOrderPriority priority;
    private String notes;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private List<LabResultDto> results;
}
