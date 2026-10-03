package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeCarePlan;
import java.time.Instant;
import java.time.LocalDate;

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
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static TreeCarePlanResponse from(TreeCarePlan plan) {
        return new TreeCarePlanResponse(
                plan.id(), plan.treeId(), plan.farmId(), plan.diagnosisId(),
                plan.diseaseCode(), plan.knowledgeArticleId(), plan.treatment(),
                plan.startDate(), plan.followUpDate(),
                plan.status() != null ? plan.status().name() : null,
                plan.createdByUserId(), plan.createdAt(), plan.updatedAt());
    }
}
