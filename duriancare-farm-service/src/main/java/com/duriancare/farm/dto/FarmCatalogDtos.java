package com.duriancare.farm.dto;

import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.ZoneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FarmCatalogDtos {

    private FarmCatalogDtos() {
    }

    public record CreateFarmRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 240) String address,
            @Size(max = 120) String province,
            @Size(max = 120) String district,
            BigDecimal latitude,
            BigDecimal longitude,
            @PositiveOrZero BigDecimal areaHectares) {
    }

    public record UpdateFarmRequest(
            @Size(max = 160) String name,
            @Size(max = 240) String address,
            @Size(max = 120) String province,
            @Size(max = 120) String district,
            BigDecimal latitude,
            BigDecimal longitude,
            @PositiveOrZero BigDecimal areaHectares,
            FarmStatus status) {
    }

    public record CreateFarmZoneRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 48) String code,
            @PositiveOrZero BigDecimal areaSquareMeters,
            Map<String, Object> boundaryGeoJson,
            @Size(max = 1000) String description) {
    }

    public record UpdateFarmZoneRequest(
            @Size(max = 160) String name,
            @Size(max = 48) String code,
            @PositiveOrZero BigDecimal areaSquareMeters,
            Map<String, Object> boundaryGeoJson,
            @Size(max = 1000) String description,
            ZoneStatus status) {
    }

    public record FarmZoneResponse(
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

    public record FarmResponse(
            String id,
            String ownerUserId,
            String name,
            String address,
            String province,
            String district,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal areaHectares,
            FarmStatus status,
            List<FarmZoneResponse> zones,
            Instant createdAt,
            Instant updatedAt) {
    }
}
