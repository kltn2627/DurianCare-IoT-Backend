package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.CultivationTaskType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateCultivationScheduleRequest(
        @NotBlank @Size(max = 64) String zoneId,
        @NotBlank @Size(max = 64) String cropId,
        @NotNull CultivationTaskType type,
        @NotNull @Future LocalDateTime scheduledAt,
        @NotBlank @Size(max = 160) String materialName,
        @NotBlank @Size(max = 80) String dosage,
        @NotBlank @Size(max = 120) String assignee,
        @NotBlank @Size(max = 80) String safetyInterval,
        @NotBlank @Size(max = 500) String notes) {
}
