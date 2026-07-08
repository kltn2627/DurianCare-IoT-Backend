package com.duriancare.notification.event.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.duriancare.notification.domain.NotificationType;
import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(new ObjectMapper(), notificationService);
    }

    @Test
    void handlesSupportedEventAndCreatesNotification() throws Exception {
        when(notificationService.createNotification(any(CreateNotificationRequest.class)))
                .thenAnswer(invocation -> {
                    CreateNotificationRequest request = invocation.getArgument(0, CreateNotificationRequest.class);
                    return new com.duriancare.notification.dto.NotificationResponse(
                            "1", request.title(), request.message(), request.type(), false,
                            Instant.now());
                });

        listener.handle("""
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "WEATHER_WARNING",
                  "receiverId": "user-1",
                  "title": "Heavy rain alert",
                  "message": "Weather warning for your farm",
                  "metadata": {"farmId": "farm-1"}
                }
                """);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.receiverId()).isEqualTo("user-1");
        org.assertj.core.api.Assertions.assertThat(request.type()).isEqualTo(NotificationType.WEATHER);
        org.assertj.core.api.Assertions.assertThat(request.sourceEventId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void ignoresMalformedPayloadWithoutThrowing() {
        assertThatCode(() -> listener.handle("not-json")).doesNotThrowAnyException();
        verifyNoInteractions(notificationService);
    }
}
