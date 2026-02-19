package com.hospital.security.config;

import com.hospital.security.authorization.PatientPermissionEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Configuration for Spring Security method-level authorization.
 *
 * Enables @PreAuthorize, @PostAuthorize, @Secured annotations on service methods
 * and registers the custom PatientPermissionEvaluator for object-level access control.
 *
 * Usage example in service layer:
 * <pre>
 * @PreAuthorize("hasPermission(#patientId, 'Patient', 'read')")
 * public Patient getPatient(Long patientId) { ... }
 *
 * @PreAuthorize("hasPermission(#patientId, 'Patient', 'write')")
 * public Patient updatePatient(Long patientId, PatientDTO data) { ... }
 *
 * @PreAuthorize("hasRole('ADMIN')")
 * public void deletePatient(Long patientId) { ... }
 * </pre>
 *
 * Note: @EnableMethodSecurity is the Spring Security 6.x replacement for
 * the deprecated @EnableGlobalMethodSecurity annotation.
 */
@Configuration
@EnableMethodSecurity  // Spring Security 6.x (replaces @EnableGlobalMethodSecurity)
public class MethodSecurityConfig {

    @Autowired
    private PatientPermissionEvaluator patientPermissionEvaluator;

    /**
     * Configures the MethodSecurityExpressionHandler to use our custom PermissionEvaluator.
     *
     * This allows @PreAuthorize annotations to use hasPermission() expressions that
     * delegate to PatientPermissionEvaluator.hasPermission().
     *
     * @return Configured MethodSecurityExpressionHandler
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(patientPermissionEvaluator);
        return handler;
    }
}
