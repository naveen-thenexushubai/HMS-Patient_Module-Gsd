package com.hospital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a RestTemplate bean for synchronous HTTP calls.
 * Used by ZipLookupService to call the Zippopotam.us API.
 *
 * Note: RestTemplate is NOT auto-configured in Spring Boot 3. Must be declared explicitly.
 * WebClient (reactive) would be preferred but requires spring-boot-starter-webflux dependency.
 * RestTemplate is sufficient for infrequent, cached ZIP code lookups.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
