package com.duriancare.notification.dto;

import com.duriancare.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record CreateNotificationRequest(
        @NotBlank @Size(max = 128) String receiverId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String message,
        @NotNull NotificationType type,
        Map<String, Object> metadata,
        @Size(max = 128) String sourceEventId,
        String eventType,
        Instant occurredAt) {

    public CreateNotificationRequest(
            String receiverId,
            String title,
            String message,
            NotificationType type,
            Map<String, Object> metadata,
            String sourceEventId) {
        this(receiverId, title, message, type, metadata, sourceEventId, null, null);
    }
}
