package com.duriancare.farm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "species")
public record Species(
        @Id String id,
        String commonName,
        String scientificName,
        @Indexed(unique = true) String cultivar,
        BigDecimal expectedYieldKg,
        Integer growthDurationDays,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
