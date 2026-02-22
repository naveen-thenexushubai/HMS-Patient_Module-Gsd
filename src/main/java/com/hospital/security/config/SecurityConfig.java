package com.hospital.security.config;

import com.hospital.security.jwt.JwtAuthenticationEntryPoint;
import com.hospital.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration with JWT authentication.
 * Configures stateless JWT-based authentication with role-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:admin123}")
    private String adminPassword;

    /**
     * Configure security filter chain with JWT authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless JWT authentication
            .csrf(csrf -> csrf.disable())

            // Configure session management - stateless
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - staff authentication
                .requestMatchers("/api/auth/**").permitAll()

                // Public endpoints - patient portal registration and login
                .requestMatchers("/api/portal/auth/**").permitAll()

                // Actuator endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Patient portal self-service (PATIENT role only)
                .requestMatchers("/api/portal/v1/**").hasRole("PATIENT")

                // Admin-only endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Patient API endpoints - staff roles
                .requestMatchers("/api/v1/**")
                    .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")

                // FHIR endpoints - authenticated staff
                .requestMatchers("/fhir/**")
                    .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")

                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // Configure exception handling
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // Add JWT filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password encoder bean for BCrypt hashing.
     * Uses strength 12 for balance between security and performance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * In-memory users covering all four roles for development.
     * admin / admin123        → ADMIN
     * receptionist / pass123  → RECEPTIONIST
     * doctor / pass123        → DOCTOR
     * nurse / pass123         → NURSE
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername(adminUsername)
                .password(encoder.encode(adminPassword))
                .roles("ADMIN")
                .build(),
            User.withUsername("receptionist")
                .password(encoder.encode("pass123"))
                .roles("RECEPTIONIST")
                .build(),
            User.withUsername("doctor")
                .password(encoder.encode("pass123"))
                .roles("DOCTOR")
                .build(),
            User.withUsername("nurse")
                .password(encoder.encode("pass123"))
                .roles("NURSE")
                .build()
        );
    }

    /**
     * Expose AuthenticationManager as a bean for use in AuthController.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
