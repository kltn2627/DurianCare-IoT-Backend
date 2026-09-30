package com.duriancare.farm.dto;

import com.duriancare.farm.domain.FarmPermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FarmAlertRecipientRequest(
        @NotBlank String farmId,
        String cultivationAreaId,
        @NotNull FarmPermissionType permission) {
}
