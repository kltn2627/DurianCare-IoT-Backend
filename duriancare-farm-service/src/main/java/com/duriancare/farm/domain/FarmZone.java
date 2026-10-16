package com.duriancare.farm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.mongodb.core.mapping.Field;

public record FarmZone(
        @Field("id") String id,
        String name,
        String code,
        BigDecimal areaSquareMeters,
        Map<String, Object> boundaryGeoJson,
        String description,
        ZoneStatus status,
        Integer rowCount,
        Integer treesPerRow,
        Instant createdAt,
        Instant updatedAt) {
}
