package com.duriancare.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.profile.engineer-documents")
public record EngineerDocumentStorageProperties(
        boolean enabled,
        String bucket,
        String region,
        String accessKeyId,
        String secretAccessKey,
        String prefix,
        String localDirectory,
        String publicBaseUrl,
        long maxUploadSizeBytes,
        List<String> allowedContentTypes) {

    public EngineerDocumentStorageProperties {
        if (prefix == null || prefix.isBlank()) {
            prefix = "engineer-documents";
        } else {
            prefix = prefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        }
        if (maxUploadSizeBytes <= 0) {
            throw new IllegalArgumentException("Engineer document max upload size must be positive");
        }
        if (localDirectory == null || localDirectory.isBlank()) {
            localDirectory = "uploads/engineer-documents";
        }
        if (publicBaseUrl != null && publicBaseUrl.isBlank()) {
            publicBaseUrl = null;
        }
        if (enabled) {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("AWS S3 bucket is required when engineer documents are enabled");
            }
            if (region == null || region.isBlank()) {
                throw new IllegalArgumentException("AWS region is required when engineer documents are enabled");
            }
            if (accessKeyId == null || accessKeyId.isBlank()) {
                throw new IllegalArgumentException("AWS access key is required when engineer documents are enabled");
            }
            if (secretAccessKey == null || secretAccessKey.isBlank()) {
                throw new IllegalArgumentException("AWS secret access key is required when engineer documents are enabled");
            }
            if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
                throw new IllegalArgumentException("Engineer document allowed content types are required when storage is enabled");
            }
        }
    }
}
