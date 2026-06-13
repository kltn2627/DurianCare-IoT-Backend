package com.duriancare.auth.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_connections")
@CompoundIndex(
        name = "requester_recipient_unique_idx",
        def = "{'requesterUserId': 1, 'recipientUserId': 1}",
        unique = true)
public record UserConnection(
        @Id String id,
        String requesterUserId,
        @Indexed String recipientUserId,
        ConnectionType connectionType,
        ConnectionStatus status,
        Instant requestedAt,
        Instant respondedAt,
        Instant createdAt,
        Instant updatedAt) {
}
