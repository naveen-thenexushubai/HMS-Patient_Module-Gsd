package com.hospital.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Storage service for consent document PDFs.
 * Separate from FileStorageService (which handles patient photos - JPEG/PNG only).
 * Validates PDF content type and stores with UUID-based filename.
 */
@Service
public class ConsentDocumentStorageService {

    private final Path storageLocation;

    public ConsentDocumentStorageService(@Value("${app.storage.consents-dir}") String consentsDir) {
        this.storageLocation = Paths.get(consentsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Could not create consent document storage directory: " + consentsDir, e);
        }
    }

    /**
     * Store a PDF file. Validates content type = application/pdf.
     * Generates UUID-based filename to prevent path traversal.
     *
     * @param file the uploaded multipart file
     * @return UUID-based filename stored on disk
     * @throws IllegalArgumentException if content type is not application/pdf
     * @throws IllegalStateException if file cannot be written to disk
     */
    public String storePdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException(
                "Only PDF documents are accepted for consent records. Received: " + contentType);
        }

        String filename = UUID.randomUUID() + ".pdf";
        Path target = storageLocation.resolve(filename).normalize();
        if (!target.startsWith(storageLocation)) {
            throw new IllegalArgumentException("Invalid filename — path traversal detected");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store consent document: " + filename, e);
        }

        return filename;
    }

    /**
     * Load a consent PDF as Spring Resource for HTTP streaming.
     *
     * @param filename the UUID-based filename returned by storePdf()
     * @return Resource for use in ResponseEntity
     * @throws IllegalStateException if file does not exist or is not readable
     */
    public Resource loadPdf(String filename) {
        try {
            Path filePath = storageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Consent document not found or not readable: " + filename);
            }
            return resource;
        } catch (Exception e) {
            throw new IllegalStateException("Could not load consent document: " + filename, e);
        }
    }
}
