package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserConnectionSource;
import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.domain.UserRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserConnectionResponse(
        UUID id,
        UUID requesterId,
        UUID receiverId,
        UserRole requesterRole,
        UserRole receiverRole,
        UserConnectionStatus status,
        UserConnectionSource source,
        ConnectionUserSummary user,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime respondedAt,
        LocalDateTime disconnectedAt) {
}
