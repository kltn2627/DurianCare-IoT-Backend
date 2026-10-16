package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cultivation_plans")
@CompoundIndex(name = "plan_farm_zone_season_idx", def = "{'farmId': 1, 'plotId': 1, 'cultivationSeasonId': 1}")
public record CultivationPlan(
        @Id String id,
        @Indexed String farmId,
        @Indexed String plotId,
        @Indexed String cultivationSeasonId,
        String templateId,
        String name,
        LocalDate startDate,
        LocalDate expectedHarvestDate,
        List<String> targetMarketCodes,
        @Indexed PlanStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
