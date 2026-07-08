package com.duriancare.auth.event.publisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.config.AuthProperties;
import com.duriancare.auth.event.UserRegisteredEvent;
import com.duriancare.auth.exception.EventPublicationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;

    private AuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AuthEventPublisher(
                kafkaTemplate,
                objectMapper,
                new AuthProperties(
                        Duration.ofMinutes(5),
                        5,
                        Duration.ofSeconds(60),
                        "duriancare.auth.events"));
    }

    @Test
    void publishRetriesOnceWhenKafkaTemporarilyFails() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.registration(
                "farmer@example.com",
                "Nguyen Van A",
                "123456",
                5);
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventType\":\"USER_REGISTERED\"}");
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("temporary broker issue"));
        CompletableFuture<SendResult<String, String>> success = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failed, success);

        publisher.publish(event);

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishFailsWhenKafkaIsUnavailableTwice() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.registration(
                "farmer@example.com",
                "Nguyen Van A",
                "123456",
                5);
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventType\":\"USER_REGISTERED\"}");
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failed, failed);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(EventPublicationException.class)
                .hasMessageContaining("could not be published");
    }
}
