package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeCareTask;
import java.time.Instant;
import java.time.LocalDate;

public record TreeCareTaskResponse(
        String id,
        String carePlanId,
        String diagnosisId,
        String treeId,
        String treeCode,
        String farmId,
        String farmZoneId,
        LocalDate scheduledDate,
        String title,
        String description,
        String status,
        String source,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt) {

    public static TreeCareTaskResponse from(TreeCareTask task) {
        return new TreeCareTaskResponse(
                task.id(), task.carePlanId(), task.diagnosisId(), task.treeId(), task.treeCode(),
                task.farmId(), task.farmZoneId(), task.scheduledDate(), task.title(), task.description(),
                task.status() == null ? null : task.status().name(), task.source(), task.sortOrder(),
                task.createdAt(), task.updatedAt());
    }
}
