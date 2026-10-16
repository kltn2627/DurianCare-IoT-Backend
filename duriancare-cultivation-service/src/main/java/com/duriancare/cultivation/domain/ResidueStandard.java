package com.duriancare.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "residue_standards")
@CompoundIndex(
        name = "residue_market_commodity_ingredient_idx",
        def = "{'marketCode': 1, 'commodityCode': 1, 'activeIngredientCode': 1}")
@CompoundIndex(
        name = "residue_lookup_active_effective_idx",
        def = "{'marketCode': 1, 'commodityCode': 1, 'activeIngredientCode': 1, 'active': 1, 'effectiveFrom': -1}")
public record ResidueStandard(
        @Id String id,
        @Indexed String marketCode,
        @Indexed String commodityCode,
        String commodityName,
        @Indexed String activeIngredientCode,
        String activeIngredientName,
        BigDecimal mrlValue,
        String unit,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        SourceType sourceType,
        String sourceReference,
        @Version Long version,
        boolean verified,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
