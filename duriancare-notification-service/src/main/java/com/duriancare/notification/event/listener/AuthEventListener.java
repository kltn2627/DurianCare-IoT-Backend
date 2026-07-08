package com.duriancare.notification.event.listener;

import com.duriancare.notification.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuthEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthEventListener.class);
    private static final Set<String> SUPPORTED_EVENT_TYPES =
            Set.of("USER_REGISTERED", "REGISTRATION_OTP_RESENT");

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public AuthEventListener(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @KafkaListener(
            topics = "${duriancare.notification.auth-events-topic:duriancare.auth.events}",
            groupId = "${spring.kafka.consumer.group-id:duriancare-notification-service}")
    public void handle(String payload) {
        AuthNotificationEvent event = deserialize(payload);
        if (event == null || !SUPPORTED_EVENT_TYPES.contains(event.eventType())) {
            return;
        }
        if (event.email() == null || event.otpCode() == null || event.expiresInMinutes() <= 0) {
            LOGGER.warn("Skipping malformed authentication event payload: {}", payload);
            return;
        }
        try {
            emailService.sendOtpEmail(event.email(), event.otpCode(), event.expiresInMinutes());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to process authentication event {}", event.eventId(), exception);
        }
    }

    private AuthNotificationEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AuthNotificationEvent.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Skipping malformed authentication event JSON", exception);
            return null;
        }
    }
}
