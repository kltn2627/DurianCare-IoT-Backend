package com.duriancare.farm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.PersistenceCreator;
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
        Instant updatedAt,
        FarmZoneAgronomy agronomy) {

    @PersistenceCreator
    public FarmZone {
    }

    /**
     * Backward-compatible constructor for existing callers that do not yet
     * provide Tree Map configuration or agronomy information.
     */
    public FarmZone(
            String id,
            String name,
            String code,
            BigDecimal areaSquareMeters,
            Map<String, Object> boundaryGeoJson,
            String description,
            ZoneStatus status,
            Instant createdAt,
            Instant updatedAt) {

        this(
                id,
                name,
                code,
                areaSquareMeters,
                boundaryGeoJson,
                description,
                status,
                null,
                null,
                createdAt,
                updatedAt,
                null);
    }
}