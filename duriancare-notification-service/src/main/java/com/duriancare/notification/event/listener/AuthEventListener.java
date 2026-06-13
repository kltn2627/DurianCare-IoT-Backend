package com.duriancare.notification.event.listener;

import com.duriancare.notification.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuthEventListener {

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
        if (!SUPPORTED_EVENT_TYPES.contains(event.eventType())) {
            return;
        }
        if (event.email() == null || event.otpCode() == null || event.expiresInMinutes() <= 0) {
            throw new IllegalArgumentException("Authentication event payload is incomplete");
        }
        emailService.sendOtpEmail(event.email(), event.otpCode(), event.expiresInMinutes());
    }

    private AuthNotificationEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AuthNotificationEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Authentication event is not valid JSON", exception);
        }
    }
}
