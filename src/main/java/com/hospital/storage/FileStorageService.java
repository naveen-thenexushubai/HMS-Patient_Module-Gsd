package com.hospital.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/**
 * Local filesystem implementation of patient photo storage.
 *
 * Storage directory is configurable via app.storage.photos-dir (default: /var/hospital/patient-photos).
 * Files are stored OUTSIDE the web-accessible root and served only via authenticated endpoint.
 *
 * UUID-based filenames prevent path traversal attacks. ImageIO magic-byte check validates
 * actual image content beyond MIME type claim. Max file size enforced at Spring multipart layer.
 */
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png");

    private final Path storageLocation;

    public FileStorageService(@Value("${app.storage.photos-dir}") String photosDir) {
        this.storageLocation = Paths.get(photosDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Could not create photo storage directory: " + photosDir, e);
        }
    }

    /**
     * Store an uploaded photo file. Validates content type (MIME + magic bytes) and generates
     * a UUID-based filename to prevent path traversal.
     *
     * @param file the uploaded multipart file (JPEG or PNG only)
     * @return the UUID-based filename stored on disk (for database storage)
     * @throws IllegalArgumentException if content type is invalid or file is not a readable image
     * @throws IllegalStateException    if file cannot be written to disk
     */
    public String store(MultipartFile file) {
        // 1. Validate declared content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Only JPEG and PNG images are accepted. Received: " + contentType);
        }

        // 2. Validate actual image content via magic bytes (prevents content-type spoofing)
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new IllegalArgumentException(
                    "Uploaded file is not a valid image (magic byte validation failed)");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read uploaded file as image", e);
        }

        // 3. Generate UUID-based filename — never use original filename (path traversal risk)
        String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
        String filename = UUID.randomUUID() + extension;

        // 4. Resolve target path and verify it is within the storage root (prevent path traversal)
        Path target = storageLocation.resolve(filename).normalize();
        if (!target.startsWith(storageLocation)) {
            throw new IllegalArgumentException("Invalid filename — path traversal detected");
        }

        // 5. Write file to disk
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store photo file: " + filename, e);
        }

        return filename;
    }

    /**
     * Load a photo file as a Spring Resource for streaming in HTTP response.
     *
     * @param filename the UUID-based filename returned by store()
     * @return Resource for use in ResponseEntity&lt;Resource&gt;
     * @throws IllegalStateException if file does not exist or is not readable
     */
    public Resource load(String filename) {
        try {
            Path filePath = storageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Photo file not found or not readable: " + filename);
            }
            return resource;
        } catch (Exception e) {
            throw new IllegalStateException("Could not load photo: " + filename, e);
        }
    }
}
