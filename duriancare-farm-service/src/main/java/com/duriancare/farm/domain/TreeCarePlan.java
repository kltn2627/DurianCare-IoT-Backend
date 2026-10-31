package com.duriancare.farm.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tree_care_plans")
public record TreeCarePlan(
        @Id String id,
        @Indexed String treeId,
        String farmId,
        String diagnosisId,
        String diseaseCode,
        String knowledgeArticleId,
        String treatment,
        LocalDate startDate,
        LocalDate followUpDate,
        TreeCarePlanStatus status,
        String source,
        String recommendationId,
        Map<String, Object> recommendationSnapshot,
        List<String> taskIds,
        Instant acceptedAt,
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
