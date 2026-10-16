package com.duriancare.auth.knowledge;

import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeImageStorageService {

    private final Path rootDirectory;
    private final long maxUploadSizeBytes;

    public KnowledgeImageStorageService(
            @Value("${duriancare.knowledge.images.local-directory:uploads/knowledge-covers}") String localDirectory,
            @Value("${duriancare.knowledge.images.max-upload-size-bytes:5242880}") long maxUploadSizeBytes) {
        this.rootDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public StoredKnowledgeImage upload(UUID articleId, MultipartFile image) {
        byte[] content = readBytes(image);
        String contentType = resolveContentType(image.getContentType(), image.getOriginalFilename());
        validateImage(content, contentType);
        String extension = resolveExtension(contentType);
        String objectKey = "%s/%s/%s.%s".formatted(
                LocalDate.now(ZoneOffset.UTC).toString().replace("-", "/"),
                articleId,
                UUID.randomUUID().toString().replace("-", ""),
                extension);
        Path target = rootDirectory.resolve(objectKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new InvalidRequestException("Knowledge image path is invalid");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store knowledge image", exception);
        }
        return new StoredKnowledgeImage(objectKey, "/api/knowledge/images/" + articleId, contentType);
    }

    public KnowledgeImageResource load(String objectKey, String contentType) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ResourceNotFoundException("Knowledge image was not found");
        }
        Path target = rootDirectory.resolve(objectKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new InvalidRequestException("Knowledge image path is invalid");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Knowledge image was not found");
            }
            return new KnowledgeImageResource(resource, contentType == null ? "image/jpeg" : contentType);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load knowledge image", exception);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read knowledge image", exception);
        }
    }

    private void validateImage(byte[] content, String contentType) {
        if (content.length == 0) {
            throw new InvalidRequestException("Knowledge image is empty");
        }
        if (content.length > maxUploadSizeBytes) {
            throw new InvalidRequestException("Knowledge image exceeds the maximum allowed size");
        }
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp")) {
            throw new InvalidRequestException("Knowledge image must be JPG, PNG, or WEBP");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null && !contentType.equals("image/webp")) {
                throw new InvalidRequestException("Knowledge image is invalid");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not validate knowledge image", exception);
        }
    }

    private String resolveContentType(String contentType, String originalFilename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
        String filename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}

