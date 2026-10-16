package com.duriancare.farm.dto;

import com.duriancare.farm.domain.FarmPermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateAgronomistInvitationRequest(
        @NotBlank String agronomistId,
        @Size(max = 1000) String message,
        List<FarmPermissionType> initialPermissions,
        List<String> initialAllowedCultivationAreaIds,
        Instant expiresAt) {
}
