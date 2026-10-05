package com.duriancare.farm.domain;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tree_diagnosis_records")
@CompoundIndex(name = "tree_diagnosed_at_idx", def = "{'treeId': 1, 'diagnosedAt': -1}")
public record TreeDiagnosisRecord(
        @Id String id,
        @Indexed String treeId,
        String farmId,
        String farmZoneId,
        String imageUrl,
        String diseaseCode,
        String diseaseName,
        Double confidence,
        Map<String, Object> boundingBox,
        String source,
        String diagnosedByUserId,
        TreeHealthStatus impliedHealthStatus,
        Instant diagnosedAt,
        Instant createdAt) {
}
