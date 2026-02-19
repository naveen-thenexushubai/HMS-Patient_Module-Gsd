package com.hospital.security.encryption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data using AES-256-GCM.
 * Uses authenticated encryption with random IV for each encryption operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 12 bytes (96 bits) for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits for authentication tag

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt plaintext using AES-256-GCM.
     * Generates random IV for each encryption and prepends it to ciphertext.
     *
     * @param plaintext the data to encrypt
     * @return base64-encoded string containing IV + ciphertext
     * @throws IllegalStateException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmParameterSpec);

            // Encrypt plaintext
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Combine IV + ciphertext and encode to base64
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new IllegalStateException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     * Extracts IV from first 12 bytes and decrypts remaining bytes.
     *
     * @param ciphertext base64-encoded string containing IV + ciphertext
     * @return decrypted plaintext
     * @throws IllegalStateException if decryption fails
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }

        try {
            // Decode base64
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV (first 12 bytes)
            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            // Extract ciphertext (remaining bytes)
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmParameterSpec);

            // Decrypt ciphertext
            byte[] plaintext = cipher.doFinal(ciphertextBytes);

            return new String(plaintext);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new IllegalStateException("Failed to decrypt data", e);
        }
    }

}
