package com.duriancare.farm.dto;

import com.duriancare.farm.domain.AgronomistInvitationStatus;
import com.duriancare.farm.domain.FarmPermissionType;
import java.time.Instant;
import java.util.List;

public record AgronomistInvitationResponse(
        String id,
        String farmId,
        String ownerId,
        String agronomistId,
        AgronomistInvitationStatus status,
        String message,
        List<FarmPermissionType> initialPermissions,
        List<String> initialAllowedCultivationAreaIds,
        Instant createdAt,
        Instant respondedAt,
        Instant expiresAt) {
}
