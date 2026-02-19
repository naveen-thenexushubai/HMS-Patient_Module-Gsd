package com.hospital.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for transparent field-level encryption of sensitive PHI.
 * Use with @Convert(converter = SensitiveDataConverter.class) on entity fields.
 *
 * NOT auto-applied - must be explicitly specified on fields that need encryption.
 */
@Slf4j
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class SensitiveDataConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;

    /**
     * Convert entity attribute to database column (encrypt).
     *
     * @param attribute the entity attribute value (plaintext)
     * @return encrypted database column value
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        log.debug("Encrypting sensitive data for database storage");
        return encryptionService.encrypt(attribute);
    }

    /**
     * Convert database column to entity attribute (decrypt).
     *
     * @param dbData the database column value (encrypted)
     * @return decrypted entity attribute value
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        log.debug("Decrypting sensitive data from database");
        return encryptionService.decrypt(dbData);
    }

}
