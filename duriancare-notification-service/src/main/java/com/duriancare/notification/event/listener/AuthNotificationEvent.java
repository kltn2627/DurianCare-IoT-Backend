package com.duriancare.notification.event.listener;

import java.time.Instant;
import java.util.UUID;

public record AuthNotificationEvent(
        UUID eventId,
        String eventType,
        String email,
        String fullName,
        String otpCode,
        long expiresInMinutes,
        Instant occurredAt) {
}
