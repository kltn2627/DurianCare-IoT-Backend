package com.duriancare.farm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "durian_trees")
@CompoundIndex(
        name = "zone_tree_code_unique_idx",
        def = "{'farmZoneId': 1, 'treeCode': 1}",
        unique = true)
public record DurianTree(
        @Id String id,
        String farmId,
        String farmZoneId,
        String speciesId,
        String treeCode,
        LocalDate plantedDate,
        BigDecimal latitude,
        BigDecimal longitude,
        TreeHealthStatus healthStatus,
        TreeStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
