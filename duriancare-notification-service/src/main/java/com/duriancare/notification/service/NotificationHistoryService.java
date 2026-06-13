package com.duriancare.notification.service;

import com.duriancare.notification.domain.NotificationHistory;
import com.duriancare.notification.repository.NotificationHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationHistoryService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificationHistoryService.class);
    private static final String OTP_SUBJECT = "Ma xac nhan DurianCare";

    private final NotificationHistoryRepository repository;

    public NotificationHistoryService(NotificationHistoryRepository repository) {
        this.repository = repository;
    }

    public void recordOtpSent(String recipient) {
        save(recipient, "SENT", null, Instant.now());
    }

    public void recordOtpFailure(String recipient, String failureReason) {
        save(recipient, "FAILED", failureReason, null);
    }

    public List<NotificationHistory> findRecentByRecipient(String recipient) {
        return repository.findTop50ByRecipientOrderByCreatedAtDesc(recipient);
    }

    private void save(
            String recipient,
            String status,
            String failureReason,
            Instant sentAt) {
        NotificationHistory history = new NotificationHistory();
        history.setRecipient(recipient);
        history.setType("OTP");
        history.setChannel("EMAIL");
        history.setSubject(OTP_SUBJECT);
        history.setStatus(status);
        history.setFailureReason(failureReason);
        history.setSentAt(sentAt);
        history.setCreatedAt(Instant.now());
        try {
            repository.save(history);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Unable to persist notification history for recipient {}",
                    recipient,
                    exception);
        }
    }
}
