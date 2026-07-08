package com.duriancare.notification.service.impl;

import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationStatus;
import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.dto.NotificationBulkUpdateResponse;
import com.duriancare.notification.dto.NotificationCountResponse;
import com.duriancare.notification.dto.NotificationPageResponse;
import com.duriancare.notification.dto.NotificationResponse;
import com.duriancare.notification.mapper.NotificationMapper;
import com.duriancare.notification.repository.NotificationRepository;
import com.duriancare.notification.service.NotificationAccessDeniedException;
import com.duriancare.notification.service.NotificationNotFoundException;
import com.duriancare.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    public NotificationServiceImpl(
            NotificationRepository repository,
            NotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        String sourceEventId = normalizeSourceEventId(request.sourceEventId());
        if (sourceEventId != null && repository.existsBySourceEventId(sourceEventId)) {
            Notification existing = repository.findBySourceEventId(sourceEventId)
                    .orElseThrow(() -> new IllegalStateException("Duplicate event disappeared"));
            return mapper.toResponse(existing);
        }

        Notification notification = mapper.toDocument(request);
        notification.setSourceEventId(sourceEventId);
        Notification saved = repository.save(notification);
        return mapper.toResponse(saved);
    }

    @Override
    public NotificationPageResponse findNotifications(String receiverId, Pageable pageable) {
        String normalizedReceiverId = normalizeReceiverId(receiverId);
        Page<Notification> page = repository.findByReceiverIdOrderByCreatedAtDesc(
                normalizedReceiverId,
                pageable);
        return mapper.toPageResponse(sortProperty(pageable), sortDirection(pageable), page);
    }

    @Override
    public NotificationPageResponse findUnreadNotifications(String receiverId, Pageable pageable) {
        String normalizedReceiverId = normalizeReceiverId(receiverId);
        Page<Notification> page = repository.findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
                normalizedReceiverId,
                pageable);
        return mapper.toPageResponse(sortProperty(pageable), sortDirection(pageable), page);
    }

    @Override
    public NotificationResponse markAsRead(String receiverId, String notificationId) {
        Notification notification = findOwnedNotification(receiverId, notificationId);
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setStatus(NotificationStatus.READ);
            notification.setUpdatedAt(Instant.now());
            notification = repository.save(notification);
        }
        return mapper.toResponse(notification);
    }

    @Override
    public NotificationBulkUpdateResponse markAllAsRead(String receiverId) {
        String normalizedReceiverId = normalizeReceiverId(receiverId);
        List<Notification> unreadNotifications = repository
                .findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
                        normalizedReceiverId,
                        Pageable.unpaged())
                .getContent();
        if (unreadNotifications.isEmpty()) {
            return new NotificationBulkUpdateResponse(0);
        }
        Instant now = Instant.now();
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setStatus(NotificationStatus.READ);
            notification.setUpdatedAt(now);
        });
        repository.saveAll(unreadNotifications);
        return new NotificationBulkUpdateResponse(unreadNotifications.size());
    }

    @Override
    public void delete(String receiverId, String notificationId) {
        Notification notification = findOwnedNotification(receiverId, notificationId);
        repository.delete(notification);
    }

    @Override
    public NotificationCountResponse countUnread(String receiverId) {
        return new NotificationCountResponse(
                repository.countByReceiverIdAndIsReadFalse(normalizeReceiverId(receiverId)));
    }

    private Notification findOwnedNotification(String receiverId, String notificationId) {
        if (!StringUtils.hasText(notificationId)) {
            throw new NotificationNotFoundException("Notification not found");
        }
        return repository.findByIdAndReceiverId(
                        notificationId.trim(),
                        normalizeReceiverId(receiverId))
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found"));
    }

    private String normalizeReceiverId(String receiverId) {
        if (!StringUtils.hasText(receiverId)) {
            throw new NotificationAccessDeniedException("Current user identity is required");
        }
        return receiverId.trim();
    }

    private String normalizeSourceEventId(String sourceEventId) {
        if (!StringUtils.hasText(sourceEventId)) {
            return null;
        }
        return sourceEventId.trim();
    }

    private String sortProperty(Pageable pageable) {
        return pageable.getSort().stream()
                .findFirst()
                .map(order -> order.getProperty())
                .orElse("createdAt");
    }

    private String sortDirection(Pageable pageable) {
        return pageable.getSort().stream()
                .findFirst()
                .map(order -> order.getDirection().name().toLowerCase(Locale.ROOT))
                .orElse("desc");
    }
}
