package com.hospital.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Encryption configuration for field-level encryption.
 * Loads encryption key from environment variable and creates SecretKey bean.
 */
@Configuration
public class EncryptionConfig {

    @Value("${encryption.key}")
    private String encryptionKeyBase64;

    /**
     * Create SecretKey bean from base64-encoded encryption key.
     * Key must be 32 bytes (256 bits) for AES-256-GCM.
     *
     * @return SecretKey for AES encryption
     * @throws IllegalStateException if key is not 32 bytes
     */
    @Bean
    public SecretKey encryptionKey() {
        byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);

        if (decodedKey.length != 32) {
            throw new IllegalStateException(
                "Encryption key must be 32 bytes (256 bits) for AES-256-GCM. Current: " + decodedKey.length
            );
        }

        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

}
