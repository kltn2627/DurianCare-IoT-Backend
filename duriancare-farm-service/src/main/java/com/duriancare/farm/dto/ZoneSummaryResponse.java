package com.duriancare.farm.dto;

import java.math.BigDecimal;

public record ZoneSummaryResponse(
        String id,
        String name,
        String code,
        BigDecimal areaSquareMeters,
        String status,
        long treeCount) {
}
