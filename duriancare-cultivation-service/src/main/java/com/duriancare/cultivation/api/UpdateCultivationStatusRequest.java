package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.CultivationTaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCultivationStatusRequest(@NotNull CultivationTaskStatus status) {
}
