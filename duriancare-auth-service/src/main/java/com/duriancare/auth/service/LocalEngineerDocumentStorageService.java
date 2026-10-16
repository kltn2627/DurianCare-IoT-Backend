package com.duriancare.auth.service;

import com.duriancare.auth.config.EngineerDocumentStorageProperties;
import com.duriancare.auth.exception.InvalidRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(
        prefix = "duriancare.profile.engineer-documents",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class LocalEngineerDocumentStorageService implements EngineerDocumentStorageService {

    private final EngineerDocumentStorageProperties properties;
    private final Path rootDirectory;

    public LocalEngineerDocumentStorageService(EngineerDocumentStorageProperties properties) {
        this.properties = properties;
        this.rootDirectory = Path.of(properties.localDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public List<StoredEngineerDocument> upload(UUID userId, List<MultipartFile> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new InvalidRequestException("At least one qualification document is required");
        }
        List<StoredEngineerDocument> storedDocuments = new ArrayList<>();
        try {
            for (MultipartFile document : documents) {
                storedDocuments.add(uploadSingle(userId, document));
            }
            return storedDocuments;
        } catch (RuntimeException exception) {
            for (StoredEngineerDocument storedDocument : storedDocuments) {
                try {
                    delete(storedDocument.documentUrl());
                } catch (RuntimeException ignored) {
                    // best-effort cleanup
                }
            }
            throw exception;
        }
    }

    @Override
    public void delete(String documentUrl) {
        String objectKey = extractObjectKey(documentUrl);
        Path target = rootDirectory.resolve(objectKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new EngineerDocumentStorageException("Document path is invalid");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new EngineerDocumentStorageException("Could not delete engineer document from local storage", exception);
        }
    }

    private StoredEngineerDocument uploadSingle(UUID userId, MultipartFile document) {
        byte[] content = readBytes(document);
        validateDocument(content, document.getContentType(), document.getOriginalFilename());
        String objectKey = buildObjectKey(userId, document.getOriginalFilename(), document.getContentType());
        Path target = rootDirectory.resolve(objectKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new EngineerDocumentStorageException("Document path is invalid");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return new StoredEngineerDocument(
                    objectKey,
                    buildDocumentUrl(objectKey),
                    safeFileName(document.getOriginalFilename()),
                    resolveContentType(document.getContentType(), document.getOriginalFilename()),
                    content.length);
        } catch (IOException exception) {
            throw new EngineerDocumentStorageException("Could not upload engineer document to local storage", exception);
        }
    }

    private byte[] readBytes(MultipartFile document) {
        try {
            return document.getBytes();
        } catch (IOException exception) {
            throw new EngineerDocumentStorageException("Could not read engineer document", exception);
        }
    }

    private void validateDocument(byte[] content, String contentType, String originalFilename) {
        if (content.length == 0) {
            throw new InvalidRequestException("Qualification document is empty");
        }
        if (content.length > properties.maxUploadSizeBytes()) {
            throw new InvalidRequestException("Qualification document exceeds the maximum allowed size");
        }
        String normalizedContentType = normalizeContentType(contentType, originalFilename);
        if (!isAllowedContentType(normalizedContentType, originalFilename)) {
            throw new InvalidRequestException("Qualification documents must be PDF, JPG, or PNG files");
        }
        if (normalizedContentType.startsWith("image/")) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                if (image == null) {
                    throw new InvalidRequestException("Qualification image is invalid");
                }
            } catch (IOException exception) {
                throw new EngineerDocumentStorageException("Could not validate qualification image", exception);
            }
        }
        if (normalizedContentType.equals("application/pdf") && !looksLikePdf(content)) {
            throw new InvalidRequestException("Qualification PDF is invalid");
        }
    }

    private boolean looksLikePdf(byte[] content) {
        return content.length >= 4
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F';
    }

    private boolean isAllowedContentType(String contentType, String originalFilename) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (properties.allowedContentTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals)) {
            return true;
        }
        String extension = extractExtension(originalFilename);
        return switch (extension) {
            case "pdf" -> normalized.equals("application/pdf");
            case "jpg", "jpeg" -> normalized.startsWith("image/jpeg");
            case "png" -> normalized.startsWith("image/png");
            default -> false;
        };
    }

    private String resolveContentType(String contentType, String originalFilename) {
        String normalized = normalizeContentType(contentType, originalFilename);
        if (!normalized.isBlank()) {
            return normalized;
        }
        String extension = extractExtension(originalFilename);
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private String normalizeContentType(String contentType, String originalFilename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
        String extension = extractExtension(originalFilename);
        if (extension == null) {
            return "";
        }
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private String buildObjectKey(UUID userId, String originalFilename, String contentType) {
        String extension = extractExtension(originalFilename);
        if (extension == null) {
            extension = resolveExtension(contentType);
        }
        String datePath = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "/");
        return String.format(
                Locale.ROOT,
                "%s/%s/%s.%s",
                datePath,
                userId,
                UUID.randomUUID().toString().replace("-", ""),
                extension);
    }

    private String resolveExtension(String contentType) {
        if (contentType == null) {
            return "pdf";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("pdf")) {
            return "pdf";
        }
        if (normalized.contains("png")) {
            return "png";
        }
        return "jpg";
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String fileName = originalFilename.trim();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return extension.length() > 5 ? null : extension;
    }

    private String buildDocumentUrl(String objectKey) {
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl().replaceAll("/+$", "") + "/" + objectKey;
        }
        return "local://" + objectKey;
    }

    private String extractObjectKey(String documentUrl) {
        if (documentUrl == null || documentUrl.isBlank()) {
            throw new EngineerDocumentStorageException("Document URL is missing");
        }
        if (documentUrl.startsWith("local://")) {
            return documentUrl.substring("local://".length());
        }
        if (properties.publicBaseUrl() != null && documentUrl.startsWith(properties.publicBaseUrl())) {
            return documentUrl.substring(properties.publicBaseUrl().length()).replaceAll("^/+", "");
        }
        return documentUrl.replaceAll("^/+", "");
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "qualification-document";
        }
        return originalFilename.trim();
    }
}
