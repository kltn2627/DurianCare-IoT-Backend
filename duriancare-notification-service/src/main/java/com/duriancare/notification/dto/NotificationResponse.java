package com.duriancare.notification.dto;

import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Map;

public record NotificationResponse(
        String id,
        String title,
        String message,
        NotificationType type,
        boolean isRead,
        Instant createdAt,
        Map<String, Object> metadata) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getMetadata() == null ? Map.of() : notification.getMetadata());
    }
}
