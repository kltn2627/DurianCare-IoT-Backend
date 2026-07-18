package com.duriancare.farm.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "agronomist_invitations")
@CompoundIndex(
        name = "farm_agronomist_invitation_status_idx",
        def = "{'farmId': 1, 'agronomistId': 1, 'status': 1}")
public record AgronomistInvitation(
        @Id String id,
        String farmId,
        String ownerId,
        @Indexed String agronomistId,
        AgronomistInvitationStatus status,
        boolean blocksNewInvitation,
        String message,
        List<FarmPermissionType> initialPermissions,
        List<String> initialAllowedCultivationAreaIds,
        Instant createdAt,
        Instant respondedAt,
        Instant expiresAt) {
}
