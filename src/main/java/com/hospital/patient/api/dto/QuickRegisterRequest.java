package com.hospital.patient.api.dto;

import com.hospital.patient.domain.Gender;
import com.hospital.patient.api.validation.ValidPhoneNumber;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Request body for POST /api/v1/patients/quick (SC1 — quick registration).
 *
 * Contains minimal required fields for walk-in patient registration.
 * Resulting patient has isRegistrationComplete=false and photoIdVerified=false.
 *
 * INTENTIONALLY EXCLUDED vs RegisterPatientRequest:
 *   - @AssertTrue photoIdVerified: walk-in patients have NOT had photo ID verified
 *   - bloodGroup, allergies, chronicConditions: not required for quick registration
 *   - emergencyContacts: can be added later via /emergency-contacts endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickRegisterRequest {

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

    // Optional fields — can be provided at time of quick registration or completed later

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
