package com.duriancare.farm.event.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FarmNotificationEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmNotificationEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public FarmNotificationEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${duriancare.notification.events-topic:${KAFKA_ALERT_TOPIC:duriancare.notification.events}}")
            String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(
            String eventType,
            String receiverId,
            String title,
            String message,
            Map<String, Object> metadata) {
        FarmNotificationEvent event = new FarmNotificationEvent(
                UUID.randomUUID(),
                eventType,
                receiverId,
                title,
                message,
                "EXPERT",
                metadata == null ? Map.of() : metadata,
                Instant.now());
        try {
            kafkaTemplate.send(topic, receiverId, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to serialize farm notification event {}", eventType, exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to publish farm notification event {}", eventType, exception);
        }
    }
}
