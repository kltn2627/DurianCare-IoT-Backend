package com.duriancare.farm.domain;

import java.time.Instant;
import java.time.LocalDate;
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
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
