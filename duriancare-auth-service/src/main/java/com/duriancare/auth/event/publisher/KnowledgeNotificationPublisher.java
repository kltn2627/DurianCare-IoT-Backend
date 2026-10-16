package com.duriancare.auth.event.publisher;

import com.duriancare.auth.event.KnowledgeNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeNotificationPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeNotificationPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String notificationTopic;

    public KnowledgeNotificationPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${duriancare.notification.events-topic:${NOTIFICATION_EVENTS_TOPIC:duriancare.notification.events}}")
            String notificationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.notificationTopic = notificationTopic;
    }

    public void publish(KnowledgeNotificationEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to serialize knowledge notification {}", event.eventId(), exception);
            return;
        }

        try {
            kafkaTemplate.send(notificationTopic, event.receiverId(), payload)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            LOGGER.warn("Unable to publish knowledge notification {}", event.eventId(), exception);
                        }
                    });
            kafkaTemplate.flush();
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to publish knowledge notification {}", event.eventId(), exception);
        }
    }
}
