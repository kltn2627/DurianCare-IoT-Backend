package com.duriancare.farm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TreeDetailResponse(
        String id,
        String farmId,
        String farmZoneId,
        String speciesId,
        String treeCode,
        String nickname,
        String variety,
        LocalDate plantedDate,
        BigDecimal latitude,
        BigDecimal longitude,
        Double positionX,
        Double positionY,
        String healthStatus,
        String status,
        String notes,
        long diagnosisCount,
        Instant latestDiagnosisAt,
        String latestDiseaseCode,
        Double latestConfidence,
        Instant createdAt,
        Instant updatedAt) {
}
