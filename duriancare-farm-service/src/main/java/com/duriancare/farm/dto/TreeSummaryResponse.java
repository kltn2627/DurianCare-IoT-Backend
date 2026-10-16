package com.duriancare.farm.dto;

import java.time.Instant;

public record TreeSummaryResponse(
        String id,
        String treeCode,
        String nickname,
        String variety,
        Double positionX,
        Double positionY,
        String healthStatus,
        String status,
        Instant latestDiagnosisAt,
        String latestDiseaseCode,
        long diagnosisCount) {
}
