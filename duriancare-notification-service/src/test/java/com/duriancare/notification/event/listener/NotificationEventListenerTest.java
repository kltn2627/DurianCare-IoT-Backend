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
                            Instant.now(), request.metadata());
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
    void handlesKnowledgeSubmittedEvent() {
        listener.handle("""
                {
                  "eventId": "22222222-2222-2222-2222-222222222222",
                  "eventType": "KNOWLEDGE_SUBMITTED",
                  "receiverId": "admin-1",
                  "title": "Có bài kiến thức chờ duyệt",
                  "message": "Kỹ sư đã gửi bài mới.",
                  "metadata": {"targetUrl": "/dashboard/admin/knowledge"}
                }
                """);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.receiverId()).isEqualTo("admin-1");
        org.assertj.core.api.Assertions.assertThat(request.metadata())
                .containsEntry("targetUrl", "/dashboard/admin/knowledge");
    }

    @Test
    void handlesIotThresholdEventAsDeviceNotification() {
        listener.handle("""
                {
                  "eventId": "33333333-3333-3333-3333-333333333333",
                  "eventType": "TEMPERATURE_HIGH",
                  "receiverId": "owner-1",
                  "title": "Cảnh báo IoT",
                  "message": "Nhiệt độ vượt ngưỡng.",
                  "metadata": {"farmId": "farm-1", "deviceUid": "esp32-1"}
                }
                """);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.type()).isEqualTo(NotificationType.DEVICE);
        org.assertj.core.api.Assertions.assertThat(request.receiverId()).isEqualTo("owner-1");
    }

    @Test
    void handlesIotOfflineEventAsDeviceNotification() {
        listener.handle("""
                {
                  "eventId": "44444444-4444-4444-4444-444444444444",
                  "eventType": "DEVICE_OFFLINE",
                  "receiverId": "owner-1",
                  "title": "Cảnh báo IoT",
                  "message": "Thiết bị mất kết nối.",
                  "metadata": {"farmId": "farm-1", "deviceUid": "esp32-1"}
                }
                """);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.type()).isEqualTo(NotificationType.DEVICE);
    }

    @Test
    void handlesIotRecoveryEventAsDeviceNotification() {
        listener.handle("""
                {
                  "eventId": "55555555-5555-5555-5555-555555555555",
                  "eventType": "IOT_ALERT_RECOVERED",
                  "receiverId": "owner-1",
                  "title": "IoT alert đã phục hồi",
                  "message": "Thiết bị đã trở lại bình thường.",
                  "metadata": {"farmId": "farm-1", "deviceUid": "esp32-1"}
                }
                """);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.type()).isEqualTo(NotificationType.DEVICE);
    }

    @Test
    void ignoresMalformedPayloadWithoutThrowing() {
        assertThatCode(() -> listener.handle("not-json")).doesNotThrowAnyException();
        verifyNoInteractions(notificationService);
    }
}
