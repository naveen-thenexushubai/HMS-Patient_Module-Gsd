package com.hospital.patient.api.dto;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.api.validation.ValidPhoneNumber;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Request body for PUT /api/v1/patients/{businessId}.
 * Contains updateable demographic fields only.
 *
 * EXCLUDED (read-only or managed separately):
 *   - patientId: read-only (UPD-03)
 *   - registeredAt/registeredBy: read-only (UPD-04)
 *   - photoIdVerified: preserved from current version by service
 *   - status: use PATCH /status endpoint (STAT-01/STAT-02)
 *   - emergencyContacts: use /emergency-contacts endpoints (EMR-01/EMR-04)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePatientRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "Phone number is required")
    @ValidPhoneNumber
    private String phoneNumber;

    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 50)
    private String state;

    @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Invalid ZIP code format")
    private String zipCode;
}
