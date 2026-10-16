package com.duriancare.farm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ZoneDetailResponse(
        String id,
        String farmId,
        String name,
        String code,
        BigDecimal areaSquareMeters,
        Map<String, Object> boundaryGeoJson,
        String description,
        String status,
        long treeCount,
        long activeTreeCount,
        Instant createdAt,
        Instant updatedAt) {
}
