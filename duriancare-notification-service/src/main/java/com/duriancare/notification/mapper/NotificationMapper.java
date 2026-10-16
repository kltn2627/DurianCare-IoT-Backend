package com.duriancare.notification.mapper;

import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationStatus;
import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.dto.NotificationPageResponse;
import com.duriancare.notification.dto.NotificationResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public Notification toDocument(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setReceiverId(request.receiverId());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setType(request.type());
        notification.setStatus(NotificationStatus.UNREAD);
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        notification.setUpdatedAt(Instant.now());
        notification.setMetadata(request.metadata());
        notification.setSourceEventId(request.sourceEventId());
        return notification;
    }

    public NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.from(notification);
    }

    public NotificationPageResponse toPageResponse(
            String sortBy,
            String sortDirection,
            Page<Notification> page) {
        return NotificationPageResponse.from(sortBy, sortDirection, page);
    }
}
