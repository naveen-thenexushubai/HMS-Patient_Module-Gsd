package com.hospital.smartform.api;

import com.hospital.smartform.api.dto.ZipLookupResponse;
import com.hospital.smartform.application.InsuranceSuggestionService;
import com.hospital.smartform.application.ZipLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Smart form assistance endpoints (SC4 — smart forms).
 *
 * GET /api/v1/smart-form/zip/{zipCode}
 *   - Calls Zippopotam.us (cached) to return city and state for a US ZIP code
 *   - Returns 200 with ZipLookupResponse on success
 *   - Returns 404 if ZIP code not found or invalid
 *   - Requires authentication (any role)
 *
 * GET /api/v1/smart-form/insurance-plans
 *   - Returns curated list of common US insurance providers from application.yml
 *   - Returns 200 with List&lt;String&gt; of provider names
 *   - Requires authentication (any role)
 *
 * These endpoints assist registration form clients in reducing data entry time:
 * - ZIP lookup pre-fills city and state fields
 * - Insurance plans provides a suggestion dropdown for the provider name field
 */
@RestController
@RequestMapping("/api/v1/smart-form")
public class SmartFormController {

    @Autowired
    private ZipLookupService zipLookupService;

    @Autowired
    private InsuranceSuggestionService insuranceSuggestionService;

    /**
     * Look up city and state for a US ZIP code.
     *
     * @param zipCode 5-digit US ZIP (e.g., "90210")
     * @return 200 with city/state data; 404 if ZIP not found
     */
    @GetMapping("/zip/{zipCode}")
    public ResponseEntity<ZipLookupResponse> lookupZip(@PathVariable String zipCode) {
        Optional<ZipLookupResponse> result = zipLookupService.lookup(zipCode);
        return result
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get list of common US insurance provider name suggestions.
     * Configured in application.yml under app.smart-form.insurance-plans.
     *
     * @return 200 with list of insurance provider names (10 providers by default)
     */
    @GetMapping("/insurance-plans")
    public ResponseEntity<List<String>> getInsurancePlans() {
        List<String> plans = insuranceSuggestionService.getInsurancePlans();
        return ResponseEntity.ok(plans);
    }
}
