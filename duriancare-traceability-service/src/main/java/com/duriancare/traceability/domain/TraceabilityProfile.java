package com.duriancare.traceability.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "traceability_profiles")
public record TraceabilityProfile(
        @Id String id,
        String farmId,
        @Indexed(unique = true) String cropSeasonId,
        @Indexed(unique = true) String publicSlug,
        String title,
        TraceabilityStatus status,
        Instant publishedAt,
        Instant lastAggregatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
