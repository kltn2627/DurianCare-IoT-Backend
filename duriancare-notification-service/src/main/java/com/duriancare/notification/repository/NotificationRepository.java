package com.duriancare.notification.repository;

import com.duriancare.notification.domain.Notification;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByReceiverIdOrderByCreatedAtDesc(String receiverId, Pageable pageable);

    Page<Notification> findByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(
            String receiverId,
            Pageable pageable);

    Optional<Notification> findByIdAndReceiverId(String id, String receiverId);

    Optional<Notification> findBySourceEventId(String sourceEventId);

    boolean existsBySourceEventId(String sourceEventId);

    long countByReceiverIdAndIsReadFalse(String receiverId);

    @Query("{ 'receiverId': ?0, 'isRead': false }")
    @Update("{ '$set': { 'isRead': true, 'status': 'READ', 'updatedAt': ?1 } }")
    long markUnreadAsRead(String receiverId, Instant updatedAt);
}
