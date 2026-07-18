package com.duriancare.farm.dto;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmPermissionType;
import java.time.Instant;
import java.util.List;

public record FarmAuthorizationResponse(
        String id,
        String farmId,
        String ownerId,
        String agronomistId,
        AuthorizationStatus status,
        List<FarmPermissionType> permissions,
        List<String> allowedCultivationAreaIds,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
