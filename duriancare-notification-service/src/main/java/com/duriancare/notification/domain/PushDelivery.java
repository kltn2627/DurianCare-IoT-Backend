package com.duriancare.notification.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Push state of one notification, keyed by the notification id so the same notification can only ever have
 * one delivery. It lives apart from the notification because inbox writes save the whole notification
 * document and would otherwise overwrite delivery state set concurrently by the sender.
 */
@Document(collection = "push_deliveries")
@CompoundIndex(name = "status_next_attempt_idx", def = "{'status': 1, 'nextAttemptAt': 1}")
public class PushDelivery {

    @Id
    private String notificationId;

    private String receiverId;

    private PushDeliveryStatus status;

    private int attempts;

    private Instant nextAttemptAt;

    private Instant claimedAt;

    private int acceptedCount;

    private int invalidTokenCount;

    /** Null until a first attempt recorded a transient failure; then only these tokens are retried. */
    private List<String> retryDeviceTokenIds;

    /** Error code only; never a raw token or a full provider message. */
    private String lastError;

    private Instant sentAt;

    @Indexed(name = "created_at_ttl_idx", expireAfter = "30d")
    private Instant createdAt;

    private Instant updatedAt;

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public PushDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(PushDeliveryStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(int acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public int getInvalidTokenCount() {
        return invalidTokenCount;
    }

    public void setInvalidTokenCount(int invalidTokenCount) {
        this.invalidTokenCount = invalidTokenCount;
    }

    public List<String> getRetryDeviceTokenIds() {
        return retryDeviceTokenIds;
    }

    public void setRetryDeviceTokenIds(List<String> retryDeviceTokenIds) {
        this.retryDeviceTokenIds = retryDeviceTokenIds;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
