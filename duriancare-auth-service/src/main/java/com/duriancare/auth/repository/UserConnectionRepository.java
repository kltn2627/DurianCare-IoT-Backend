package com.duriancare.auth.repository;

import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.entity.UserConnection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserConnectionRepository extends JpaRepository<UserConnection, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserConnection> findByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    @Query("""
            select c from UserConnection c
            where c.userLowId = :userLowId and c.userHighId = :userHighId
            """)
    Optional<UserConnection> findPair(@Param("userLowId") UUID userLowId, @Param("userHighId") UUID userHighId);

    List<UserConnection> findByReceiverIdAndStatusOrderByCreatedAtDesc(UUID receiverId, UserConnectionStatus status);

    List<UserConnection> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID requesterId, UserConnectionStatus status);

    @Query("""
            select c from UserConnection c
            where (c.requesterId = :userId or c.receiverId = :userId)
              and c.status in :statuses
            order by c.respondedAt desc, c.updatedAt desc
            """)
    Page<UserConnection> findForUser(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<UserConnectionStatus> statuses,
            Pageable pageable);
}
