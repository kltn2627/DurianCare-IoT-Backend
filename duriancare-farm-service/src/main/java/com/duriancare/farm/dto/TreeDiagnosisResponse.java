package com.duriancare.farm.dto;

import java.time.Instant;
import java.util.Map;

public record TreeDiagnosisResponse(
        String id,
        String treeId,
        String treeCode,
        String imageUrl,
        String diseaseCode,
        String diseaseName,
        Double confidence,
        Map<String, Object> boundingBox,
        String source,
        String impliedHealthStatus,
        Instant diagnosedAt,
        Instant createdAt) {
}
