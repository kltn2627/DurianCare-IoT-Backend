package com.duriancare.auth.event.publisher;

import com.duriancare.auth.config.AuthProperties;
import com.duriancare.auth.event.UserRegisteredEvent;
import com.duriancare.auth.exception.EventPublicationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthEventPublisher.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(250);
    private static final long SEND_TIMEOUT_SECONDS = 5L;

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
            publishWithRetry(event.email(), payload);
        } catch (JsonProcessingException exception) {
            throw new EventPublicationException("Unable to serialize authentication event", exception);
        }
    }

    private void publishWithRetry(String key, String payload) {
        EventPublicationException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                kafkaTemplate.send(properties.eventsTopic(), key, payload)
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new EventPublicationException(
                        "Authentication event could not be published",
                        exception);
            } catch (Exception exception) {
                lastFailure = new EventPublicationException(
                        "Authentication event could not be published",
                        exception);
                if (attempt < MAX_ATTEMPTS) {
                    LOGGER.warn(
                            "Authentication event publish attempt {} failed, retrying once",
                            attempt,
                            exception);
                    pauseBeforeRetry();
                }
            }
        }
        throw lastFailure;
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException(
                    "Authentication event could not be published",
                    exception);
        }
    }
}
