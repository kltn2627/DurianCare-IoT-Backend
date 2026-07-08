package com.duriancare.auth.service;

import com.duriancare.auth.config.AvatarStorageProperties;
import com.duriancare.auth.exception.InvalidRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Service
@ConditionalOnProperty(prefix = "duriancare.profile.avatar", name = "enabled", havingValue = "true")
public class S3AvatarStorageService implements AvatarStorageService {

    private static final List<String> FALLBACK_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png");

    private final AvatarStorageProperties properties;
    private final S3Client s3Client;

    public S3AvatarStorageService(AvatarStorageProperties properties, S3Client s3Client) {
        this.properties = properties;
        this.s3Client = s3Client;
    }

    @Override
    public StoredAvatar upload(UUID userId, MultipartFile avatar) {
        byte[] content = readBytes(avatar);
        validateImage(content, avatar.getContentType());
        String objectKey = buildObjectKey(userId, avatar.getOriginalFilename(), avatar.getContentType());
        String resolvedContentType = resolveContentType(avatar.getContentType(), avatar.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .contentType(resolvedContentType)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(content));
            return new StoredAvatar(objectKey, buildObjectUrl(objectKey));
        } catch (RuntimeException exception) {
            throw new AvatarStorageException("Could not upload avatar to S3", exception);
        }
    }

    @Override
    public void delete(String avatarUrl) {
        String objectKey = extractObjectKey(avatarUrl);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new AvatarStorageException("Could not delete avatar from S3", exception);
        }
    }

    private byte[] readBytes(MultipartFile avatar) {
        try {
            return avatar.getBytes();
        } catch (IOException exception) {
            throw new AvatarStorageException("Could not read avatar file", exception);
        }
    }

    private void validateImage(byte[] content, String contentType) {
        if (content.length == 0) {
            throw new InvalidRequestException("Avatar file is empty");
        }
        if (content.length > properties.maxUploadSizeBytes()) {
            throw new InvalidRequestException("Avatar file exceeds the maximum allowed size");
        }
        if (contentType == null || !isAllowedContentType(contentType)) {
            throw new InvalidRequestException("Avatar must be a JPEG or PNG image");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new InvalidRequestException("Avatar file is not a valid image");
            }
        } catch (IOException exception) {
            throw new AvatarStorageException("Could not validate avatar image", exception);
        }
    }

    private boolean isAllowedContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return properties.allowedContentTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals)
                || FALLBACK_CONTENT_TYPES.contains(normalized);
    }

    private String resolveContentType(String contentType, String originalFilename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        String extension = extractExtension(originalFilename);
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
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
                "%s/%s/%s/%s.%s",
                properties.prefix(),
                datePath,
                userId,
                UUID.randomUUID().toString().replace("-", ""),
                extension);
    }

    private String resolveExtension(String contentType) {
        if (contentType == null) {
            return "jpg";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("png")) {
            return "png";
        }
        if (normalized.contains("webp")) {
            return "webp";
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

    private String buildObjectUrl(String objectKey) {
        return String.format(
                Locale.ROOT,
                "https://%s.s3.%s.amazonaws.com/%s",
                properties.bucket(),
                properties.region(),
                objectKey);
    }

    private String extractObjectKey(String avatarUrl) {
        try {
            URI uri = URI.create(avatarUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Avatar URL path is missing");
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (RuntimeException exception) {
            throw new AvatarStorageException("Avatar URL is invalid", exception);
        }
    }
}
