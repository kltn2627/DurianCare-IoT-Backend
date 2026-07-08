package com.duriancare.notification.dto;

import com.duriancare.notification.domain.NotificationHistory;
import java.time.Instant;

public record NotificationHistoryResponse(
        String id,
        String recipient,
        String type,
        String channel,
        String subject,
        String status,
        String failureReason,
        Instant sentAt,
        Instant createdAt) {

    public static NotificationHistoryResponse from(NotificationHistory history) {
        return new NotificationHistoryResponse(
                history.getId(),
                history.getRecipient(),
                history.getType(),
                history.getChannel(),
                history.getSubject(),
                history.getStatus(),
                history.getFailureReason(),
                history.getSentAt(),
                history.getCreatedAt());
    }
}
