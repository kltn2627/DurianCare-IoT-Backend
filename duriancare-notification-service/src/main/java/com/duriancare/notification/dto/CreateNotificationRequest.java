package com.duriancare.notification.dto;

import com.duriancare.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateNotificationRequest(
        @NotBlank @Size(max = 128) String receiverId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String message,
        @NotNull NotificationType type,
        Map<String, Object> metadata,
        @Size(max = 128) String sourceEventId) {
}
