package com.duriancare.notification.repository;

import com.duriancare.notification.domain.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByReceiverIdOrderByCreatedAtDesc(String receiverId, Pageable pageable);

    Page<Notification> findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
            String receiverId,
            Pageable pageable);

    Optional<Notification> findByIdAndReceiverId(String id, String receiverId);

    Optional<Notification> findBySourceEventId(String sourceEventId);

    boolean existsBySourceEventId(String sourceEventId);

    long countByReceiverIdAndIsReadFalse(String receiverId);
}
