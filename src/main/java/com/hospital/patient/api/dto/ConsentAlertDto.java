package com.hospital.patient.api.dto;

import com.hospital.patient.domain.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentAlertDto {
    private ConsentType consentType;
    private String alertType;   // "MISSING" or "EXPIRED"
    private Instant expiredAt;  // populated only for EXPIRED alerts
}
