package com.duriancare.traceability.domain;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "traceability_snapshots")
@CompoundIndex(
        name = "profile_version_unique_idx",
        def = "{'traceabilityProfileId': 1, 'version': 1}",
        unique = true)
public record TraceabilitySnapshot(
        @Id String id,
        String traceabilityProfileId,
        int version,
        Map<String, Object> farmSnapshot,
        Map<String, Object> environmentSummary,
        Map<String, Object> diseaseHistory,
        Map<String, Object> treatmentHistory,
        Map<String, Object> harvestSummary,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
