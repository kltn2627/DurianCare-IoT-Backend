package com.duriancare.auth.event.publisher;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.event.KnowledgeNotificationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KnowledgeNotificationPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KnowledgeNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KnowledgeNotificationPublisher(
                kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(),
                "duriancare.notification.events");
    }

    @Test
    void publishSendsAndFlushesKnowledgeNotification() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
        KnowledgeNotificationEvent event = new KnowledgeNotificationEvent(
                UUID.randomUUID(),
                "KNOWLEDGE_APPROVED",
                UUID.randomUUID().toString(),
                "Bai kien thuc da duoc duyet",
                "Bai viet da duoc hien thi.",
                "GENERAL",
                Map.of("articleId", UUID.randomUUID().toString()),
                Instant.now());

        publisher.publish(event);

        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
        verify(kafkaTemplate).flush();
    }
}
