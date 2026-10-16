package com.duriancare.notification.dto;

import com.duriancare.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Page;

public record NotificationPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        int numberOfElements,
        boolean hasNext,
        boolean hasPrevious,
        String sortBy,
        String sortDirection,
        List<NotificationResponse> notifications) {

    public static NotificationPageResponse from(
            String sortBy,
            String sortDirection,
            Page<Notification> notificationsPage) {
        return new NotificationPageResponse(
                notificationsPage.getNumber(),
                notificationsPage.getSize(),
                notificationsPage.getTotalElements(),
                notificationsPage.getTotalPages(),
                notificationsPage.getNumberOfElements(),
                notificationsPage.hasNext(),
                notificationsPage.hasPrevious(),
                sortBy,
                sortDirection,
                notificationsPage.getContent().stream()
                        .map(NotificationResponse::from)
                        .toList());
    }
}
