package com.hospital.security.encryption;

import com.hospital.security.config.EncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EncryptionService.
 * Verifies AES-256-GCM encryption with random IV.
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // Create 32-byte encryption key for AES-256
        byte[] keyBytes = Base64.getDecoder().decode("MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");
        SecretKey secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
        encryptionService = new EncryptionService(secretKey);
    }

    @Test
    void encryptDecrypt_roundTrip() {
        String plaintext = "123-45-6789";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted, "Encrypted value should not be null");
        assertNotEquals(plaintext, encrypted, "Encrypted value should differ from plaintext");

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted, "Decrypted value should match original plaintext");
    }

    @Test
    void encrypt_differentIVs() {
        String plaintext = "123-45-6789";
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertext with different IVs");
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        String result = encryptionService.encrypt(null);
        assertNull(result, "Encrypting null should return null");
    }

    @Test
    void decrypt_nullInput_returnsNull() {
        String result = encryptionService.decrypt(null);
        assertNull(result, "Decrypting null should return null");
    }

    @Test
    void encryptDecrypt_emptyString() {
        String plaintext = "";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted, "Encrypted empty string should not be null");
        assertNotEquals("", encrypted, "Encrypted empty string should not be empty");

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted, "Decrypted value should match empty string");
    }

    @Test
    void encryptDecrypt_longString() {
        String plaintext = "This is a much longer string that contains multiple sentences. " +
                          "It includes various characters: numbers 1234567890, symbols !@#$%^&*(), " +
                          "and special unicode characters: \u00E9\u00F1\u00FC. " +
                          "This tests that encryption works correctly with longer data.";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted, "Long string should encrypt and decrypt correctly");
    }

}
