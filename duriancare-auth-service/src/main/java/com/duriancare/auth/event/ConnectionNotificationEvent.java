package com.duriancare.auth.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConnectionNotificationEvent(
        UUID eventId,
        String eventType,
        String receiverId,
        String title,
        String message,
        String notificationType,
        Map<String, Object> metadata,
        Instant occurredAt) {
}
