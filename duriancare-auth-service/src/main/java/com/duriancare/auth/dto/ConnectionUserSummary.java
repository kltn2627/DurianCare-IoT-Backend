package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import java.util.UUID;

public record ConnectionUserSummary(
        UUID id,
        String fullName,
        String phoneNumber,
        String avatar,
        UserRole role,
        UserStatus status,
        String region,
        String specialization,
        ConnectionRelationStatus relationStatus,
        UUID connectionId) {
}
