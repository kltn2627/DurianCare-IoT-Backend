package com.duriancare.farm.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "farm_authorizations")
@CompoundIndex(
        name = "scope_engineer_unique_idx",
        def = "{'farmId': 1, 'farmZoneId': 1, 'engineerUserId': 1}",
        unique = true,
        sparse = true)
public record FarmAuthorization(
        @Id String id,
        String farmId,
        String farmZoneId,
        String ownerUserId,
        @Indexed String engineerUserId,
        String initiatedByUserId,
        AuthorizationInvitationType invitationType,
        AuthorizationStatus status,
        List<FarmPermission> permissions,
        Instant validFrom,
        Instant validUntil,
        Instant approvedAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt) {
}
