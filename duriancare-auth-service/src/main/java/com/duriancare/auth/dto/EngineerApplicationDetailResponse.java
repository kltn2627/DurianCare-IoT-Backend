package com.duriancare.auth.dto;

import com.duriancare.auth.domain.EngineerApplicationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EngineerApplicationDetailResponse(
        UUID applicationId,
        UUID userId,
        String email,
        String fullName,
        String workplace,
        String specialization,
        Integer yearsExperience,
        String biography,
        EngineerApplicationStatus status,
        String rejectionReason,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<EngineerApplicationDocumentResponse> documents) {
}
