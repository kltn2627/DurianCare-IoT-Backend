package com.duriancare.traceability.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "qr_traceability")
@CompoundIndex(
        name = "profile_active_idx",
        def = "{'traceabilityProfileId': 1, 'active': 1}")
public record QrTraceability(
        @Id String id,
        String traceabilityProfileId,
        String snapshotId,
        String publicUrl,
        String qrImageUrl,
        @Indexed(unique = true) String tokenHash,
        Instant expiresAt,
        boolean active,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
