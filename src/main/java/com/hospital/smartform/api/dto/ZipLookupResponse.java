package com.hospital.smartform.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for ZIP code lookup (SC4 — smart form ZIP auto-complete).
 * Returned by GET /api/v1/smart-form/zip/{zipCode}.
 */
@Data
@Builder
public class ZipLookupResponse {

    private String zipCode;

    /** Primary city name for this ZIP code (e.g., "Beverly Hills"). */
    private String city;

    /** Full state name (e.g., "California"). */
    private String state;

    /** Two-letter state abbreviation (e.g., "CA"). */
    private String stateAbbreviation;
}
