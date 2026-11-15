package com.duriancare.notification.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PushMessageFactoryTest {

    private final PushMessageFactory factory = new PushMessageFactory();

    private static Notification notification(Map<String, Object> metadata) {
        Notification notification = new Notification();
        notification.setId("noti-1");
        notification.setReceiverId("user-1");
        notification.setTitle("Cảnh báo");
        notification.setMessage("Độ ẩm đất thấp");
        notification.setType(NotificationType.DEVICE);
        notification.setSourceEventId("evt-1");
        notification.setEventType("SOIL_MOISTURE_LOW");
        notification.setOccurredAt(Instant.parse("2026-11-04T01:02:03Z"));
        notification.setCreatedAt(Instant.parse("2026-11-04T01:02:04Z"));
        notification.setMetadata(metadata);
        return notification;
    }

    @Test
    void carriesStableIdentifiersAndNavigationData() {
        PushMessage message = factory.create(notification(Map.of(
                "alertId", 42, "deviceUid", "esp32-1", "farmId", "farm-1")));

        assertThat(message.notificationId()).isEqualTo("noti-1");
        assertThat(message.data())
                .containsEntry("schemaVersion", "1")
                .containsEntry("notificationId", "noti-1")
                .containsEntry("eventId", "evt-1")
                .containsEntry("eventType", "SOIL_MOISTURE_LOW")
                .containsEntry("notificationType", "DEVICE")
                .containsEntry("occurredAt", "2026-11-04T01:02:03Z")
                .containsEntry("alertId", "42")
                .containsEntry("deviceUid", "esp32-1");
    }

    @Test
    void metadataOutsideTheAllowListStaysOnTheServer() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("peerPhoneNumber", "0901234567");
        metadata.put("email", "user@example.com");
        metadata.put("accessToken", "secret");
        metadata.put("treeId", "tree-1");
        metadata.put("farmId", Map.of("nested", "value"));

        PushMessage message = factory.create(notification(metadata));

        assertThat(message.data()).containsEntry("treeId", "tree-1")
                .doesNotContainKeys("peerPhoneNumber", "email", "accessToken", "farmId");
    }

    @Test
    void neverUsesKeysThatExpoNotificationsReinterprets() {
        PushMessage message = factory.create(notification(Map.of("body", "x", "message", "y", "title", "z")));
        assertThat(message.data()).doesNotContainKeys("body", "message", "title");
    }

    @Test
    void targetUrlMustBeAnInAppPath() {
        assertThat(factory.create(notification(Map.of("targetUrl", "/dashboard/iot"))).data())
                .containsEntry("targetUrl", "/dashboard/iot");
        assertThat(factory.create(notification(Map.of("targetUrl", "https://evil.example/x"))).data())
                .doesNotContainKey("targetUrl");
        assertThat(factory.create(notification(Map.of("targetUrl", "//evil.example/x"))).data())
                .doesNotContainKey("targetUrl");
    }

    @Test
    void fallsBackToTheNotificationIdAndCreationTime() {
        Notification notification = notification(null);
        notification.setSourceEventId(null);
        notification.setOccurredAt(null);

        assertThat(factory.create(notification).data())
                .containsEntry("eventId", "noti-1")
                .containsEntry("occurredAt", "2026-11-04T01:02:04Z");
    }

    @Test
    void longTextIsTruncatedForTheNotificationPayload() {
        Notification notification = notification(Map.of());
        notification.setMessage("x".repeat(2000));

        assertThat(factory.create(notification).body()).hasSize(500).endsWith("…");
    }
}
