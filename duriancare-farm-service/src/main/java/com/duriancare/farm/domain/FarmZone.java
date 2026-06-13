package com.duriancare.farm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record FarmZone(
        String id,
        String name,
        String code,
        BigDecimal areaSquareMeters,
        Map<String, Object> boundaryGeoJson,
        String description,
        ZoneStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
