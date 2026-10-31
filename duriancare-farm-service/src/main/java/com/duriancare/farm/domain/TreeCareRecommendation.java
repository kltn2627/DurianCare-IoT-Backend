package com.duriancare.farm.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tree_care_recommendations")
@CompoundIndex(name = "diagnosis_source_idx", def = "{'diagnosisId': 1, 'source': 1}", unique = true)
public record TreeCareRecommendation(
        @Id String id,
        @Indexed String treeId,
        String farmId,
        String farmZoneId,
        String treeCode,
        @Indexed String diagnosisId,
        String diseaseCode,
        String source,
        TreeCareRecommendationStatus status,
        Map<String, Object> recommendationSnapshot,
        List<RecommendedCareTaskDraft> tasks,
        LocalDate followUpDate,
        String acceptedCarePlanId,
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
