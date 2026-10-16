package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeCarePlan;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record TreeCarePlanResponse(
        String id,
        String treeId,
        String farmId,
        String diagnosisId,
        String diseaseCode,
        String knowledgeArticleId,
        String treatment,
        LocalDate startDate,
        LocalDate followUpDate,
        String status,
        String source,
        String recommendationId,
        Map<String, Object> recommendationSnapshot,
        List<String> taskIds,
        Instant acceptedAt,
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static TreeCarePlanResponse from(TreeCarePlan plan) {
        return new TreeCarePlanResponse(
                plan.id(), plan.treeId(), plan.farmId(), plan.diagnosisId(),
                plan.diseaseCode(), plan.knowledgeArticleId(), plan.treatment(),
                plan.startDate(), plan.followUpDate(),
                plan.status() != null ? plan.status().name() : null,
                plan.source(), plan.recommendationId(), plan.recommendationSnapshot(),
                plan.taskIds(), plan.acceptedAt(),
                plan.createdByUserId(), plan.createdAt(), plan.updatedAt());
    }
}
