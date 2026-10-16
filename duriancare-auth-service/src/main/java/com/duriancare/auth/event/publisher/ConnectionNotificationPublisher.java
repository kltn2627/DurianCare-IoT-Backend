package com.duriancare.auth.event.publisher;

import com.duriancare.auth.event.ConnectionNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConnectionNotificationPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionNotificationPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String notificationTopic;

    public ConnectionNotificationPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${duriancare.notification.events-topic:${KAFKA_ALERT_TOPIC:duriancare.notification.events}}")
            String notificationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.notificationTopic = notificationTopic;
    }

    public void publish(ConnectionNotificationEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to serialize connection notification {}", event.eventId(), exception);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                kafkaTemplate.send(notificationTopic, event.receiverId(), payload)
                        .whenComplete((result, exception) -> {
                            if (exception != null) {
                                LOGGER.warn("Unable to publish connection notification {}", event.eventId(), exception);
                            }
                        });
            } catch (RuntimeException exception) {
                LOGGER.warn("Unable to publish connection notification {}", event.eventId(), exception);
            }
        });
    }
}
