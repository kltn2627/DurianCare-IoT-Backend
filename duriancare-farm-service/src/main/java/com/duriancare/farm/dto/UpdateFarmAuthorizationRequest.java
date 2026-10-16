package com.duriancare.farm.dto;

import com.duriancare.farm.domain.FarmPermissionType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record UpdateFarmAuthorizationRequest(
        @NotNull List<FarmPermissionType> permissions,
        @NotNull List<String> allowedCultivationAreaIds,
        Instant expiresAt) {
}
