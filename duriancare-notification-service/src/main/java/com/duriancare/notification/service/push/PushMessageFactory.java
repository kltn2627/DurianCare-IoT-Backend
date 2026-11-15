package com.duriancare.notification.service.push;

import com.duriancare.notification.domain.Notification;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the push payload shared with the Mobile app (contract: docs/FCM_API_MOBILE.md). FCM data travels
 * through Google, so metadata goes out by allow-list: identifiers needed to open the right screen pass,
 * anything else in metadata (phone numbers, free text) stays on the server. Mobile can always re-read the
 * full notification by id.
 */
@Component
public class PushMessageFactory {

    static final String SCHEMA_VERSION = "1";

    /** Mirrors the keys read by Mobile's notificationTargetResolver. */
    private static final Set<String> NAVIGATION_KEYS = Set.of(
            "activityId", "agronomistInvitationId", "alertId", "alertType", "articleId", "articleSlug",
            "authorizationId", "carePlanId", "chatConversationId", "communityPostId", "conversationId",
            "cultivationActivityId", "cultivationAreaId", "cultivationPlanId", "deviceId", "deviceUid",
            "diagnosisId", "entryId", "exportReleaseId", "farmAuthorizationId", "farmId", "followUpDate",
            "harvestBatchId", "invitationId", "knowledgeId", "knowledgeSlug", "peerUserId", "planId", "postId",
            "predictionId", "releaseId", "reviewId", "sensorId", "severity", "slug", "threadId",
            "traceabilityId", "treeId", "zoneId");

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_BODY_LENGTH = 500;
    private static final int MAX_DATA_VALUE_LENGTH = 200;

    public PushMessage create(Notification notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("schemaVersion", SCHEMA_VERSION);
        data.put("notificationId", notification.getId());
        data.put("eventId", StringUtils.hasText(notification.getSourceEventId())
                ? notification.getSourceEventId()
                : notification.getId());
        if (StringUtils.hasText(notification.getEventType())) {
            data.put("eventType", notification.getEventType());
        }
        if (notification.getType() != null) {
            data.put("notificationType", notification.getType().name());
        }
        Instant occurredAt = notification.getOccurredAt() != null
                ? notification.getOccurredAt()
                : notification.getCreatedAt();
        if (occurredAt != null) {
            data.put("occurredAt", occurredAt.toString());
        }
        Map<String, Object> metadata = notification.getMetadata();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (NAVIGATION_KEYS.contains(key) && isScalar(value)) {
                    String text = value.toString().trim();
                    if (!text.isEmpty() && text.length() <= MAX_DATA_VALUE_LENGTH) {
                        data.put(key, text);
                    }
                }
            });
            putTargetUrl(data, metadata.get("targetUrl"));
        }
        return new PushMessage(
                notification.getId(),
                truncate(notification.getTitle(), MAX_TITLE_LENGTH),
                truncate(notification.getMessage(), MAX_BODY_LENGTH),
                data);
    }

    /** In-app paths only; an absolute URL has no navigation meaning on the phone. */
    private void putTargetUrl(Map<String, String> data, Object value) {
        if (value instanceof String url && url.startsWith("/") && !url.startsWith("//")
                && url.length() <= MAX_DATA_VALUE_LENGTH) {
            data.put("targetUrl", url);
        }
    }

    private boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
