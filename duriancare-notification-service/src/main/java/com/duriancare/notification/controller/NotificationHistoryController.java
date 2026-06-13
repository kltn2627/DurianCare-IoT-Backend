package com.duriancare.notification.controller;

import com.duriancare.notification.domain.NotificationHistory;
import com.duriancare.notification.service.NotificationHistoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification/history")
public class NotificationHistoryController {

    private final NotificationHistoryService historyService;

    public NotificationHistoryController(NotificationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<NotificationHistory> findRecent(
            @RequestParam String recipient) {
        return historyService.findRecentByRecipient(recipient.trim().toLowerCase());
    }
}
