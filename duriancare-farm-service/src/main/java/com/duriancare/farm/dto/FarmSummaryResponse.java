package com.duriancare.farm.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FarmSummaryResponse(
        String id,
        String name,
        String address,
        String province,
        String district,
        BigDecimal areaHectares,
        String status,
        int zoneCount,
        Instant createdAt) {
}
