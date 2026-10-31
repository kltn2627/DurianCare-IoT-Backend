package com.duriancare.cultivation.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "legacy_integrity_markers")
public record LegacyIntegrityMarker(
        @Id String id,
        String collection,
        String resourceId,
        String integrityStatus,
        String reason,
        String source,
        Instant observedAt) {
}
