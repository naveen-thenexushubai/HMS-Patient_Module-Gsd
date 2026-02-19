package com.hospital.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties.
 * Loads JWT settings from application.yml with prefix "jwt".
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * JWT signing secret - minimum 64 characters required for HS512.
     * Loaded from ${JWT_SECRET} environment variable.
     */
    private String secret;

    /**
     * Token expiration time in milliseconds.
     * Default: 3600000 (1 hour)
     */
    private long expiration = 3600000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

}
