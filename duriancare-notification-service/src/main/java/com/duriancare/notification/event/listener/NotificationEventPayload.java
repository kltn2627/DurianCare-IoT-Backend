package com.duriancare.notification.event.listener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationEventPayload(
        UUID eventId,
        String sourceEventId,
        String eventType,
        String receiverId,
        String title,
        String message,
        String notificationType,
        Map<String, Object> metadata,
        Instant occurredAt) {
}
