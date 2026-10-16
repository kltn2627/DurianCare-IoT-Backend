package com.duriancare.auth.entity;

import com.duriancare.auth.domain.UserConnectionSource;
import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "user_connections")
public class UserConnection {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(name = "user_low_id", nullable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false)
    private UUID userHighId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_role", nullable = false, length = 32)
    private UserRole requesterRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "receiver_role", nullable = false, length = 32)
    private UserRole receiverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserConnectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserConnectionSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserConnection() {
    }

    public UserConnection(
            UUID requesterId,
            UUID receiverId,
            UserRole requesterRole,
            UserRole receiverRole,
            UserConnectionSource source) {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.requesterRole = requesterRole;
        this.receiverRole = receiverRole;
        this.source = source;
        this.status = UserConnectionStatus.PENDING;
        applyPair(requesterId, receiverId);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void restart(UUID requesterId, UUID receiverId, UserRole requesterRole, UserRole receiverRole, UserConnectionSource source) {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.requesterRole = requesterRole;
        this.receiverRole = receiverRole;
        this.source = source;
        this.status = UserConnectionStatus.PENDING;
        this.respondedAt = null;
        this.disconnectedAt = null;
        applyPair(requesterId, receiverId);
    }

    public void accept() {
        status = UserConnectionStatus.ACCEPTED;
        respondedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void reject() {
        status = UserConnectionStatus.REJECTED;
        respondedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void cancel() {
        status = UserConnectionStatus.CANCELLED;
        respondedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void disconnect() {
        status = UserConnectionStatus.DISCONNECTED;
        disconnectedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void applyPair(UUID firstUserId, UUID secondUserId) {
        if (firstUserId.compareTo(secondUserId) <= 0) {
            userLowId = firstUserId;
            userHighId = secondUserId;
        } else {
            userLowId = secondUserId;
            userHighId = firstUserId;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getReceiverId() {
        return receiverId;
    }

    public UUID getOtherUserId(UUID currentUserId) {
        return requesterId.equals(currentUserId) ? receiverId : requesterId;
    }

    public UserRole getRequesterRole() {
        return requesterRole;
    }

    public UserRole getReceiverRole() {
        return receiverRole;
    }

    public UserConnectionStatus getStatus() {
        return status;
    }

    public UserConnectionSource getSource() {
        return source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public long getVersion() {
        return version;
    }
}
