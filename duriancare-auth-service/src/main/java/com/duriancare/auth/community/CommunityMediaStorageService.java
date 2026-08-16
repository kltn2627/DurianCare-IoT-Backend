package com.duriancare.auth.community;

import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommunityMediaStorageService {

    private final Path rootDirectory;
    private final long maxUploadSizeBytes;

    public CommunityMediaStorageService(
            @Value("${duriancare.community.media.local-directory:uploads/community-media}") String localDirectory,
            @Value("${duriancare.community.media.max-upload-size-bytes:10485760}") long maxUploadSizeBytes) {
        this.rootDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public StoredCommunityMedia upload(UUID mediaId, MultipartFile file) {
        byte[] content = readBytes(file);
        String contentType = resolveContentType(file.getContentType(), file.getOriginalFilename());
        CommunityMediaType mediaType = resolveMediaType(contentType);
        validate(content, contentType);
        String extension = resolveExtension(contentType);
        String objectKey = "%s/%s.%s".formatted(
                LocalDate.now(ZoneOffset.UTC).toString().replace("-", "/"),
                mediaId.toString().replace("-", ""),
                extension);
        Path target = rootDirectory.resolve(objectKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new InvalidRequestException("Community media path is invalid");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store community media", exception);
        }
        return new StoredCommunityMedia(objectKey, "/api/community/media/" + mediaId, contentType, mediaType);
    }

    public CommunityMediaResource load(CommunityMedia media) {
        Path target = rootDirectory.resolve(media.getObjectKey()).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new InvalidRequestException("Community media path is invalid");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Community media was not found");
            }
            return new CommunityMediaResource(resource, media.getContentType());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load community media", exception);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read community media", exception);
        }
    }

    private void validate(byte[] content, String contentType) {
        if (content.length == 0) {
            throw new InvalidRequestException("Community media is empty");
        }
        if (content.length > maxUploadSizeBytes) {
            throw new InvalidRequestException("Community media exceeds the maximum allowed size");
        }
        if (!contentType.startsWith("image/") && !contentType.startsWith("video/")) {
            throw new InvalidRequestException("Community media must be an image or a video");
        }
    }

    private CommunityMediaType resolveMediaType(String contentType) {
        return contentType.startsWith("video/") ? CommunityMediaType.VIDEO : CommunityMediaType.IMAGE;
    }

    private String resolveContentType(String contentType, String originalFilename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
        String filename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".webp")) return "image/webp";
        if (filename.endsWith(".mp4")) return "video/mp4";
        if (filename.endsWith(".mov")) return "video/quicktime";
        return "image/jpeg";
    }

    private String resolveExtension(String contentType) {
        if (contentType.equals("image/png")) return "png";
        if (contentType.equals("image/webp")) return "webp";
        if (contentType.equals("video/mp4")) return "mp4";
        if (contentType.equals("video/quicktime")) return "mov";
        return "jpg";
    }
}
