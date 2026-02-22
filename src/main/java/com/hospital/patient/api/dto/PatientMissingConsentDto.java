package com.hospital.patient.api.dto;

import com.hospital.patient.domain.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMissingConsentDto {
    private UUID patientBusinessId;
    private String patientId;
    private String fullName;
    private List<ConsentType> missingConsents;
}
