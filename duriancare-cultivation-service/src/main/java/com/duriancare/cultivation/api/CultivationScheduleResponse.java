package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.CultivationSchedule;
import com.duriancare.cultivation.domain.CultivationTaskStatus;
import com.duriancare.cultivation.domain.CultivationTaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CultivationScheduleResponse(
        UUID id,
        String zoneId,
        String cropId,
        CultivationTaskType type,
        CultivationTaskStatus status,
        LocalDate date,
        LocalTime time,
        String materialName,
        String dosage,
        String assignee,
        String safetyInterval,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static CultivationScheduleResponse from(CultivationSchedule schedule) {
        return new CultivationScheduleResponse(
                schedule.getId(),
                schedule.getZoneId(),
                schedule.getCropId(),
                schedule.getType(),
                schedule.getStatus(),
                schedule.getScheduledDate(),
                schedule.getScheduledTime(),
                schedule.getMaterialName(),
                schedule.getDosage(),
                schedule.getAssignee(),
                schedule.getSafetyInterval(),
                schedule.getNotes(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }
}
