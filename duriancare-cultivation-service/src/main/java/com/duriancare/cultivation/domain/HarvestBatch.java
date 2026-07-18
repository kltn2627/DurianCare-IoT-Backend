package com.duriancare.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "harvest_batches")
public record HarvestBatch(
        @Id String id,
        @Indexed(unique = true) String batchCode,
        @Indexed String cultivationSeasonId,
        @Indexed String farmId,
        @Indexed String plotId,
        Instant harvestedAt,
        BigDecimal quantity,
        String quantityUnit,
        String expectedDestinationMarket,
        LocalDate latestSafeHarvestDate,
        RiskLevel chemicalRiskLevel,
        @Indexed HarvestBatchStatus status,
        String createdBy,
        Instant createdAt) {
}
