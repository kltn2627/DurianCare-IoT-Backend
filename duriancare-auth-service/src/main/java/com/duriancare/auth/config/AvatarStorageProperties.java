package com.duriancare.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.profile.avatar")
public record AvatarStorageProperties(
        boolean enabled,
        String bucket,
        String region,
        String accessKeyId,
        String secretAccessKey,
        String prefix,
        long maxUploadSizeBytes,
        List<String> allowedContentTypes) {

    public AvatarStorageProperties {
        if (prefix == null || prefix.isBlank()) {
            prefix = "avatars";
        } else {
            prefix = prefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        }
        if (maxUploadSizeBytes <= 0) {
            throw new IllegalArgumentException("Avatar max upload size must be positive");
        }
        if (enabled) {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("AWS S3 bucket is required when avatar storage is enabled");
            }
            if (region == null || region.isBlank()) {
                throw new IllegalArgumentException("AWS region is required when avatar storage is enabled");
            }
            if (accessKeyId == null || accessKeyId.isBlank()) {
                throw new IllegalArgumentException("AWS access key is required when avatar storage is enabled");
            }
            if (secretAccessKey == null || secretAccessKey.isBlank()) {
                throw new IllegalArgumentException("AWS secret access key is required when avatar storage is enabled");
            }
            if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
                throw new IllegalArgumentException("Avatar allowed content types are required when storage is enabled");
            }
        }
    }
}
