package com.hospital.smartform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for smart form assistance features.
 * Values are read from application.yml under the app.smart-form prefix.
 *
 * insurance-plans: curated list of common US insurance providers for registration form suggestion.
 * Configurable without code changes — update application.yml or provide INSURANCE_PLANS env var.
 */
@Configuration
@ConfigurationProperties(prefix = "app.smart-form")
@Data
public class SmartFormProperties {

    /**
     * List of common insurance provider names for auto-suggestion in registration forms.
     * Populated from app.smart-form.insurance-plans in application.yml.
     */
    private List<String> insurancePlans = new ArrayList<>();
}
