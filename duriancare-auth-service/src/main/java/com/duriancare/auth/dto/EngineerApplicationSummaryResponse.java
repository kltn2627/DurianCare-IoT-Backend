package com.duriancare.auth.dto;

import com.duriancare.auth.domain.EngineerApplicationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record EngineerApplicationSummaryResponse(
        UUID applicationId,
        UUID userId,
        String email,
        String fullName,
        String workplace,
        String specialization,
        Integer yearsExperience,
        EngineerApplicationStatus status,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {
}
