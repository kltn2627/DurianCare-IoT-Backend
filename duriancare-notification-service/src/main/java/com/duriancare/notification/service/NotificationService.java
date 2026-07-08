package com.duriancare.notification.service;

import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.dto.NotificationBulkUpdateResponse;
import com.duriancare.notification.dto.NotificationCountResponse;
import com.duriancare.notification.dto.NotificationPageResponse;
import com.duriancare.notification.dto.NotificationResponse;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationResponse createNotification(CreateNotificationRequest request);

    NotificationPageResponse findNotifications(String receiverId, Pageable pageable);

    NotificationPageResponse findUnreadNotifications(String receiverId, Pageable pageable);

    NotificationResponse markAsRead(String receiverId, String notificationId);

    NotificationBulkUpdateResponse markAllAsRead(String receiverId);

    void delete(String receiverId, String notificationId);

    NotificationCountResponse countUnread(String receiverId);
}
