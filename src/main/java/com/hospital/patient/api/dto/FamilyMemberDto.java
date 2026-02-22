package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FamilyMemberDto {
    private UUID patientBusinessId;
    private String patientId;
    private String fullName;
    private String relationshipToHead;
    private Boolean isHead;
    private Instant joinedAt;
}
