package com.duriancare.farm.dto;

import java.time.Instant;

public record ZoneSafetySummaryResponse(
        String zoneId,
        String zoneName,
        long totalTrees,
        long assessedTrees,
        long safeTrees,
        long attentionTrees,
        long notAssessedTrees,
        Double safetyRate,
        String safetyRateLabel,
        Instant calculatedAt) {
}
