package com.duriancare.notification.repository;

import com.duriancare.notification.domain.NotificationHistory;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationHistoryRepository
        extends MongoRepository<NotificationHistory, String> {

    List<NotificationHistory> findTop50ByRecipientOrderByCreatedAtDesc(String recipient);
}
