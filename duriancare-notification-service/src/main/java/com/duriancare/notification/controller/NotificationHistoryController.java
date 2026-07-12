package com.duriancare.notification.controller;

import com.duriancare.notification.dto.NotificationHistoryResponse;
import com.duriancare.notification.service.NotificationHistoryService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@Tag(name = "Notification History", description = "Historical email and notification delivery records")
@RequestMapping("/api/v1/notification/history")
public class NotificationHistoryController {

    private final NotificationHistoryService historyService;

    public NotificationHistoryController(NotificationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "Get recent notification history", description = "Return recent delivery history for the specified recipient email.")
    public List<NotificationHistoryResponse> findRecent(
            @RequestParam @NotBlank String recipient) {
        return historyService.findRecentByRecipient(recipient.trim().toLowerCase(Locale.ROOT))
                .stream()
                .map(NotificationHistoryResponse::from)
                .toList();
    }
}
