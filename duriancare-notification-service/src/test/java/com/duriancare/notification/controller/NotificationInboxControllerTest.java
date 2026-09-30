package com.duriancare.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duriancare.notification.domain.NotificationType;
import com.duriancare.notification.dto.NotificationBulkUpdateResponse;
import com.duriancare.notification.dto.NotificationCountResponse;
import com.duriancare.notification.dto.NotificationPageResponse;
import com.duriancare.notification.dto.NotificationResponse;
import com.duriancare.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationInboxController.class)
@Import(ApiExceptionHandler.class)
class NotificationInboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void listNotificationsReturnsPagedResponse() throws Exception {
        NotificationResponse first = new NotificationResponse(
                "1", "Title A", "Message A", NotificationType.SYSTEM, false,
                Instant.parse("2026-07-07T00:00:00Z"), Map.of("targetUrl", "/dashboard/admin/knowledge"));
        NotificationResponse second = new NotificationResponse(
                "2", "Title B", "Message B", NotificationType.DEVICE, true,
                Instant.parse("2026-07-07T00:00:00Z"), Map.of());
        when(notificationService.findNotifications(eq("user-1"), any()))
                .thenReturn(new NotificationPageResponse(
                        1,
                        5,
                        12,
                        3,
                        2,
                        true,
                        true,
                        "title",
                        "asc",
                        List.of(first, second)));

        mockMvc.perform(get("/api/notifications")
                        .header("X-Auth-User-Id", "user-1")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortBy", "title")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.notifications[0].id").value("1"))
                .andExpect(jsonPath("$.notifications[0].metadata.targetUrl").value("/dashboard/admin/knowledge"))
                .andExpect(jsonPath("$.notifications[1].id").value("2"));
    }

    @Test
    void missingAuthHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void invalidSortFieldIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("X-Auth-User-Id", "user-1")
                        .param("sortBy", "updatedAt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAsReadDelegatesToService() throws Exception {
        NotificationResponse response = new NotificationResponse(
                "1", "Title", "Body", NotificationType.GENERAL, true,
                Instant.parse("2026-07-07T00:00:00Z"), Map.of());
        when(notificationService.markAsRead("user-1", "1")).thenReturn(response);

        mockMvc.perform(patch("/api/notifications/1/read")
                        .header("X-Auth-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.isRead").value(true));

        verify(notificationService).markAsRead("user-1", "1");
    }

    @Test
    void markAllAsReadDelegatesToService() throws Exception {
        when(notificationService.markAllAsRead("user-1"))
                .thenReturn(new NotificationBulkUpdateResponse(2));

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("X-Auth-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));
    }

    @Test
    void countUnreadDelegatesToService() throws Exception {
        when(notificationService.countUnread("user-1")).thenReturn(new NotificationCountResponse(4));

        mockMvc.perform(get("/api/notifications/count")
                        .header("X-Auth-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void deleteNotificationReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/notifications/1")
                        .header("X-Auth-User-Id", "user-1"))
                .andExpect(status().isNoContent());

        verify(notificationService).delete("user-1", "1");
    }
}
