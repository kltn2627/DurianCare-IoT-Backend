package com.duriancare.farm.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "farm_authorizations")
@CompoundIndex(
        name = "farm_agronomist_status_idx",
        def = "{'farmId': 1, 'agronomistId': 1, 'status': 1}")
public record FarmAuthorization(
        @Id String id,
        String farmId,
        String ownerId,
        @Indexed String agronomistId,
        AuthorizationStatus status,
        boolean blocksNewAuthorization,
        List<FarmPermissionType> permissions,
        List<String> allowedCultivationAreaIds,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {
}
