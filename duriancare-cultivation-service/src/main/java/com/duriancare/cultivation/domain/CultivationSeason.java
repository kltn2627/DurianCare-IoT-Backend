package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cultivation_seasons")
@CompoundIndex(name = "season_farm_plot_start_idx", def = "{'farmId': 1, 'plotId': 1, 'startDate': -1}")
public record CultivationSeason(
        @Id String id,
        @Indexed String farmId,
        @Indexed String plotId,
        String name,
        String crop,
        String variety,
        LocalDate startDate,
        LocalDate endDate,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
