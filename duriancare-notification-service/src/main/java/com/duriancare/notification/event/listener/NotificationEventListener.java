package com.duriancare.notification.event.listener;

import com.duriancare.notification.domain.NotificationType;
import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NotificationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "USER_REGISTERED",
            "EXPERT_APPROVED",
            "DISEASE_DETECTED",
            "WEATHER_WARNING",
            "DEVICE_OFFLINE",
            "SYSTEM_ALERT",
            "AGRONOMIST_INVITATION_CREATED",
            "AGRONOMIST_INVITATION_ACCEPTED",
            "AGRONOMIST_INVITATION_REJECTED",
            "FARM_AUTHORIZATION_UPDATED",
            "FARM_AUTHORIZATION_REVOKED");

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventListener(
            ObjectMapper objectMapper,
            NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${duriancare.notification.events-topic:${KAFKA_ALERT_TOPIC:duriancare.notification.events}}",
            groupId = "${spring.kafka.consumer.group-id:duriancare-notification-service}")
    public void handle(String payload) {
        NotificationEventPayload event = deserialize(payload);
        if (event == null || !SUPPORTED_EVENT_TYPES.contains(normalizeEventType(event.eventType()))) {
            return;
        }
        if (!StringUtils.hasText(event.receiverId())
                || !StringUtils.hasText(event.title())
                || !StringUtils.hasText(event.message())) {
            LOGGER.warn("Skipping malformed notification event payload: {}", payload);
            return;
        }

        NotificationType type = resolveType(event);
        notificationService.createNotification(new CreateNotificationRequest(
                event.receiverId().trim(),
                event.title().trim(),
                event.message().trim(),
                type,
                event.metadata() == null ? Map.of() : event.metadata(),
                event.eventId() == null ? null : event.eventId().toString()));
    }

    private NotificationEventPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationEventPayload.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Skipping malformed notification event JSON", exception);
            return null;
        }
    }

    private String normalizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "GENERAL";
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }

    private NotificationType resolveType(NotificationEventPayload event) {
        String explicitType = event.notificationType();
        if (StringUtils.hasText(explicitType)) {
            try {
                return NotificationType.valueOf(explicitType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                LOGGER.warn("Unknown notification type '{}' for event {}", explicitType, event.eventType());
            }
        }
        return switch (normalizeEventType(event.eventType())) {
            case "USER_REGISTERED" -> NotificationType.ACCOUNT;
            case "EXPERT_APPROVED" -> NotificationType.EXPERT;
            case "DISEASE_DETECTED" -> NotificationType.DISEASE;
            case "WEATHER_WARNING" -> NotificationType.WEATHER;
            case "DEVICE_OFFLINE" -> NotificationType.DEVICE;
            case "SYSTEM_ALERT" -> NotificationType.SYSTEM;
            case "AGRONOMIST_INVITATION_CREATED",
                    "AGRONOMIST_INVITATION_ACCEPTED",
                    "AGRONOMIST_INVITATION_REJECTED",
                    "FARM_AUTHORIZATION_UPDATED",
                    "FARM_AUTHORIZATION_REVOKED" -> NotificationType.EXPERT;
            default -> NotificationType.GENERAL;
        };
    }
}
