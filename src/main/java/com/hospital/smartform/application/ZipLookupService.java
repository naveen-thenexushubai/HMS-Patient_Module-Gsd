package com.hospital.smartform.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hospital.smartform.api.dto.ZipLookupResponse;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Service for ZIP code to city/state lookup using Zippopotam.us.
 *
 * @Cacheable caches results in Caffeine "zipLookup" cache (configured in CacheConfig).
 * All 43,000 US ZIP codes fit within the 50,000 entry cache limit.
 * Cache TTL is 24 hours — ZIP assignments are extremely stable.
 *
 * API: https://api.zippopotam.us/us/{zipCode}
 * - No API key required
 * - Returns HTTP 404 for unknown ZIP codes
 * - Returns empty places[] for some edge cases — handle both
 */
@Service
public class ZipLookupService {

    private static final String ZIPPOPOTAMUS_URL = "https://api.zippopotam.us/us/{zipCode}";

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Look up city and state for a US ZIP code.
     * Result is cached with key = zipCode for 24 hours.
     *
     * @param zipCode 5-digit US ZIP code (e.g., "90210")
     * @return Optional with city/state data; empty if ZIP unknown or API unreachable
     */
    @Cacheable(value = "zipLookup", key = "#zipCode")
    public Optional<ZipLookupResponse> lookup(String zipCode) {
        try {
            ZippopotamusApiResponse response = restTemplate.getForObject(
                ZIPPOPOTAMUS_URL,
                ZippopotamusApiResponse.class,
                zipCode
            );

            // Null check: RestTemplate can return null if response body is empty
            if (response == null) {
                return Optional.empty();
            }

            // Empty places[] check: API sometimes returns 200 with empty array for edge-case ZIPs
            List<ZippopotamusApiResponse.Place> places = response.getPlaces();
            if (places == null || places.isEmpty()) {
                return Optional.empty();
            }

            ZippopotamusApiResponse.Place place = places.get(0);
            return Optional.of(ZipLookupResponse.builder()
                .zipCode(zipCode)
                .city(place.getPlaceName())
                .state(place.getState())
                .stateAbbreviation(place.getStateAbbreviation())
                .build());

        } catch (HttpClientErrorException.NotFound e) {
            // API returns 404 for unknown ZIP codes — treat as not found, not an error
            return Optional.empty();
        } catch (Exception e) {
            // API unreachable, network error, deserialization failure — log and return empty
            // Controller will return 503 or 404; caller decides
            return Optional.empty();
        }
    }

    /**
     * Inner DTO for Zippopotam.us API response deserialization.
     *
     * IMPORTANT: Zippopotam.us uses space-containing JSON keys ("place name", "state abbreviation").
     * @JsonProperty is REQUIRED on fields with spaces — Jackson cannot map these automatically.
     * Without @JsonProperty, Jackson silently leaves the fields null.
     */
    @Data
    static class ZippopotamusApiResponse {

        @JsonProperty("post code")
        private String postCode;

        @JsonProperty("country")
        private String country;

        @JsonProperty("places")
        private List<Place> places;

        @Data
        static class Place {

            @JsonProperty("place name")
            private String placeName;

            @JsonProperty("state")
            private String state;

            @JsonProperty("state abbreviation")
            private String stateAbbreviation;
        }
    }
}
