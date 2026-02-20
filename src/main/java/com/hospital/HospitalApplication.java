package com.hospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application entry point for Hospital Management System.
 *
 * HIPAA-compliant patient management system with:
 * - Spring Boot 3.4.5+ (CVE-2025-22235 fix)
 * - Environment-based secrets management
 * - JWT authentication and object-level authorization
 * - Comprehensive audit logging
 * - Data at rest and in transit encryption
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class HospitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalApplication.class, args);
    }

}
