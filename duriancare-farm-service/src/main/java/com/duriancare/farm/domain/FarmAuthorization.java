package com.duriancare.farm.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
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
        List<FarmPermissionType> permissions,
        Instant validFrom,
        Instant validUntil,
        Instant approvedAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {

    public FarmAuthorization(
            String id,
            String farmId,
            String ownerId,
            String agronomistId,
            AuthorizationStatus status,
            boolean blocksNewAuthorization,
            List<FarmPermissionType> permissions,
            List<String> allowedCultivationAreaIds,
            Instant grantedAt,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        this(
                id,
                farmId,
                encodeAllowedCultivationAreaIds(allowedCultivationAreaIds),
                ownerId,
                agronomistId,
                ownerId,
                null,
                status,
                permissions,
                grantedAt,
                expiresAt,
                blocksNewAuthorization ? null : grantedAt,
                revokedAt,
                createdAt,
                updatedAt,
                version);
    }

    public String ownerId() {
        return ownerUserId;
    }

    public String agronomistId() {
        return engineerUserId;
    }

    public List<String> allowedCultivationAreaIds() {
        return decodeAllowedCultivationAreaIds(farmZoneId);
    }

    public Instant grantedAt() {
        return validFrom;
    }

    public Instant expiresAt() {
        return validUntil;
    }

    public boolean blocksNewAuthorization() {
        return status != AuthorizationStatus.ACTIVE;
    }

    private static String encodeAllowedCultivationAreaIds(List<String> allowedCultivationAreaIds) {
        if (allowedCultivationAreaIds == null || allowedCultivationAreaIds.isEmpty()) {
            return null;
        }
        return String.join("\u001F", allowedCultivationAreaIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList());
    }

    private static List<String> decodeAllowedCultivationAreaIds(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encodedValue.split("\\u001F", -1))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }
}
