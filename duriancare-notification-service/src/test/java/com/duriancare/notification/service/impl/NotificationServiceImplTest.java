package com.duriancare.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationStatus;
import com.duriancare.notification.domain.NotificationType;
import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.mapper.NotificationMapper;
import com.duriancare.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository repository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(repository, new NotificationMapper());
    }

    @Test
    void createNotificationPersistsAndReturnsFrontendDto() {
        when(repository.existsBySourceEventId("evt-1")).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0, Notification.class);
            notification.setId("noti-1");
            return notification;
        });

        var response = service.createNotification(new CreateNotificationRequest(
                "user-1",
                "Device offline",
                "ESP32 is offline",
                NotificationType.DEVICE,
                Map.of("deviceId", "dev-1"),
                "evt-1"));

        assertThat(response.id()).isEqualTo("noti-1");
        assertThat(response.title()).isEqualTo("Device offline");
        assertThat(response.message()).isEqualTo("ESP32 is offline");
        assertThat(response.type()).isEqualTo(NotificationType.DEVICE);
        assertThat(response.isRead()).isFalse();
        verify(repository).save(any(Notification.class));
    }

    @Test
    void createNotificationReturnsExistingWhenEventAlreadyProcessed() {
        Notification existing = notification("noti-1", "user-1", "Alert", "Body",
                NotificationType.SYSTEM, false);
        existing.setSourceEventId("evt-1");
        when(repository.existsBySourceEventId("evt-1")).thenReturn(true);
        when(repository.findBySourceEventId("evt-1")).thenReturn(Optional.of(existing));

        var response = service.createNotification(new CreateNotificationRequest(
                "user-1",
                "Alert",
                "Body",
                NotificationType.SYSTEM,
                Map.of(),
                "evt-1"));

        assertThat(response.id()).isEqualTo("noti-1");
        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void findNotificationsMapsPagedResponse() {
        Notification first = notification("1", "user-1", "Title A", "Message A",
                NotificationType.ACCOUNT, false);
        Notification second = notification("2", "user-1", "Title B", "Message B",
                NotificationType.SYSTEM, true);
        PageRequest pageRequest = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "title"));
        when(repository.findByReceiverIdOrderByCreatedAtDesc(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageRequest, 11));

        var response = service.findNotifications(" user-1 ", pageRequest);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.notifications()).hasSize(2);
        verify(repository).findByReceiverIdOrderByCreatedAtDesc(eq("user-1"), any(Pageable.class));
    }

    @Test
    void findUnreadNotificationsUsesUnreadRepositoryMethod() {
        Notification unread = notification("1", "user-1", "Unread", "Body",
                NotificationType.GENERAL, false);
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(repository.findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
                eq("user-1"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unread), pageRequest, 1));

        var response = service.findUnreadNotifications("user-1", pageRequest);

        assertThat(response.notifications()).hasSize(1);
        verify(repository).findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
                eq("user-1"),
                any(Pageable.class));
    }

    @Test
    void markAsReadUpdatesStatusAndReturnsNotification() {
        Notification notification = notification("1", "user-1", "Title", "Body",
                NotificationType.GENERAL, false);
        when(repository.findByIdAndReceiverId("1", "user-1")).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.markAsRead("user-1", "1");

        assertThat(response.isRead()).isTrue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
        verify(repository).save(any(Notification.class));
    }

    @Test
    void markAllAsReadUpdatesUnreadItemsOnly() {
        Notification first = notification("1", "user-1", "Title A", "Body A",
                NotificationType.SYSTEM, false);
        Notification second = notification("2", "user-1", "Title B", "Body B",
                NotificationType.SYSTEM, false);
        when(repository.findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
                eq("user-1"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));

        var response = service.markAllAsRead("user-1");

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
        verify(repository).saveAll(any(Iterable.class));
    }

    @Test
    void deleteRemovesOwnedNotification() {
        Notification notification = notification("1", "user-1", "Title", "Body",
                NotificationType.GENERAL, false);
        when(repository.findByIdAndReceiverId("1", "user-1")).thenReturn(Optional.of(notification));

        service.delete("user-1", "1");

        verify(repository).delete(notification);
    }

    @Test
    void countUnreadReturnsRepositoryCount() {
        when(repository.countByReceiverIdAndIsReadFalse("user-1")).thenReturn(4L);

        var response = service.countUnread("user-1");

        assertThat(response.count()).isEqualTo(4L);
    }

    private Notification notification(
            String id,
            String receiverId,
            String title,
            String message,
            NotificationType type,
            boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setReceiverId(receiverId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setStatus(read ? NotificationStatus.READ : NotificationStatus.UNREAD);
        notification.setRead(read);
        notification.setCreatedAt(Instant.parse("2026-07-07T00:00:00Z"));
        notification.setUpdatedAt(Instant.parse("2026-07-07T00:00:00Z"));
        return notification;
    }
}
