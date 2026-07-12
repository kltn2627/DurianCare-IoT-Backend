package com.duriancare.notification.controller;

import com.duriancare.notification.dto.NotificationBulkUpdateResponse;
import com.duriancare.notification.dto.NotificationCountResponse;
import com.duriancare.notification.dto.NotificationPageResponse;
import com.duriancare.notification.dto.NotificationResponse;
import com.duriancare.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@Tag(name = "Notification Inbox", description = "Authenticated notification inbox for mobile and web clients")
@RequestMapping({"/api/v1/notification/notifications", "/api/notifications"})
public class NotificationInboxController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    public NotificationInboxController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List notifications", description = "Return the current user's notifications with pagination and sorting.")
    public NotificationPageResponse findNotifications(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt")
            @Pattern(regexp = "^(createdAt|title)$") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "desc")
            @Pattern(regexp = "^(asc|desc)$") String sortDirection) {
        return notificationService.findNotifications(
                currentUserId,
                pageable(page, size, sortBy, sortDirection));
    }

    @GetMapping("/unread")
    @Operation(summary = "List unread notifications", description = "Return unread notifications for the current user.")
    public NotificationPageResponse findUnreadNotifications(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt")
            @Pattern(regexp = "^(createdAt|title)$") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "desc")
            @Pattern(regexp = "^(asc|desc)$") String sortDirection) {
        return notificationService.findUnreadNotifications(
                currentUserId,
                pageable(page, size, sortBy, sortDirection));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Mark a single notification as read for the current user.")
    public NotificationResponse markAsRead(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId,
            @PathVariable @NotBlank String id) {
        return notificationService.markAsRead(currentUserId, id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Mark all notifications as read for the current user.")
    public NotificationBulkUpdateResponse markAllAsRead(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId) {
        return notificationService.markAllAsRead(currentUserId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete notification", description = "Delete a notification for the current user.")
    public void delete(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId,
            @PathVariable @NotBlank String id) {
        notificationService.delete(currentUserId, id);
    }

    @GetMapping("/count")
    @Operation(summary = "Count unread notifications", description = "Return the unread notification count for the current user.")
    public NotificationCountResponse countUnread(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId) {
        return notificationService.countUnread(currentUserId);
    }

    private Pageable pageable(int page, int size, String sortBy, String sortDirection) {
        String resolvedSortBy = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy;
        String resolvedSortDirection = sortDirection == null || sortDirection.isBlank()
                ? "desc"
                : sortDirection;
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(resolvedSortDirection), resolvedSortBy));
    }
}
