package com.duriancare.auth.event.publisher;

import com.duriancare.auth.config.AuthProperties;
import com.duriancare.auth.event.UserRegisteredEvent;
import com.duriancare.auth.exception.EventPublicationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties properties;

    public AuthEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            AuthProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(UserRegisteredEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(properties.eventsTopic(), event.email(), payload).get(5, TimeUnit.SECONDS);
        } catch (JsonProcessingException exception) {
            throw new EventPublicationException("Unable to serialize authentication event", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException("Authentication event could not be published", exception);
        } catch (Exception exception) {
            throw new EventPublicationException("Authentication event could not be published", exception);
        }
    }
}
