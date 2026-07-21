package com.duriancare.farm.dto;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmPermissionType;
import java.time.Instant;
import java.util.List;

public record AuthorizedFarmResponse(
        String authorizationId,
        String farmId,
        String farmName,
        String ownerId,
        AuthorizationStatus status,
        List<FarmPermissionType> permissions,
        List<String> allowedCultivationAreaIds,
        Instant grantedAt,
        Instant expiresAt) {
}
