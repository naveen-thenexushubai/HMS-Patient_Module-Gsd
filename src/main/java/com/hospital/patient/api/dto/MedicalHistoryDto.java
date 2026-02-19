package com.hospital.patient.api.dto;

import com.hospital.patient.domain.BloodGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalHistoryDto {
    private BloodGroup bloodGroup;
    private String allergies;
    private String chronicConditions;
}
