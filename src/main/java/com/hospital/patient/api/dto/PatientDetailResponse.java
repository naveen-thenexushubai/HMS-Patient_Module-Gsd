package com.hospital.patient.api.dto;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.PatientStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDetailResponse {
    private String patientId;
    private UUID businessId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private int age;
    private Gender gender;
    private String phoneNumber;
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private PatientStatus status;
    private List<EmergencyContactDto> emergencyContacts;
    private MedicalHistoryDto medicalHistory;
    private InsuranceDto insuranceInfo;
    private Instant registeredAt;
    private String registeredBy;
    private Instant lastModifiedAt;
    private String lastModifiedBy;
    private Long version;
}
