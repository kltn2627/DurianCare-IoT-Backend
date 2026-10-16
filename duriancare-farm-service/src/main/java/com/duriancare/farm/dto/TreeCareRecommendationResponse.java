package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeCareRecommendation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record TreeCareRecommendationResponse(
        String id,
        String treeId,
        String farmId,
        String farmZoneId,
        String treeCode,
        String diagnosisId,
        String diseaseCode,
        String source,
        String status,
        Map<String, Object> recommendationSnapshot,
        List<RecommendedCareTaskRequest> tasks,
        LocalDate followUpDate,
        String acceptedCarePlanId,
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static TreeCareRecommendationResponse from(TreeCareRecommendation recommendation) {
        return new TreeCareRecommendationResponse(
                recommendation.id(), recommendation.treeId(), recommendation.farmId(), recommendation.farmZoneId(),
                recommendation.treeCode(), recommendation.diagnosisId(), recommendation.diseaseCode(),
                recommendation.source(), recommendation.status() == null ? null : recommendation.status().name(),
                recommendation.recommendationSnapshot(),
                recommendation.tasks() == null ? List.of() : recommendation.tasks().stream()
                        .map(task -> new RecommendedCareTaskRequest(
                                task.title(), task.description(), task.scheduledDate(), task.sortOrder()))
                        .toList(),
                recommendation.followUpDate(), recommendation.acceptedCarePlanId(),
                recommendation.createdByUserId(), recommendation.createdAt(), recommendation.updatedAt());
    }
}
