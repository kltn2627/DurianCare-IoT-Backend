package com.duriancare.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EngineerApplicationDocumentResponse(
        UUID documentId,
        String fileName,
        String contentType,
        long fileSize,
        String documentUrl,
        LocalDateTime uploadedAt) {
}
