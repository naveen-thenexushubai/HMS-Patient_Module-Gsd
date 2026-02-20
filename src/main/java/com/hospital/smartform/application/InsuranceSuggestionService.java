package com.hospital.smartform.application;

import com.hospital.smartform.config.SmartFormProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for insurance plan name suggestions.
 *
 * Returns a curated list of common US insurance providers configured in application.yml
 * under app.smart-form.insurance-plans. No external API call — purely config-driven.
 *
 * The list is configurable without code changes by updating application.yml
 * or setting the INSURANCE_PLANS environment variable.
 */
@Service
public class InsuranceSuggestionService {

    @Autowired
    private SmartFormProperties smartFormProperties;

    /**
     * Get the list of suggested insurance provider names.
     *
     * @return list of insurance provider names from application.yml
     */
    public List<String> getInsurancePlans() {
        return smartFormProperties.getInsurancePlans();
    }
}
