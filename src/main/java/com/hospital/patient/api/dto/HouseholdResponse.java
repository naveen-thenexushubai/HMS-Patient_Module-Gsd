package com.hospital.patient.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class HouseholdResponse {
    private UUID householdId;
    private List<FamilyMemberDto> members;
    private int memberCount;
}
