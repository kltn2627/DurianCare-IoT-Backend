package com.duriancare.notification.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One FCM registration token. The token is globally unique, so it belongs to exactly one user at a time;
 * deviceId identifies the app installation and is how a refreshed token replaces its predecessor.
 * Inactive rows are removed by the TTL on deactivatedAt.
 */
@Document(collection = "device_tokens")
@CompoundIndex(name = "user_active_idx", def = "{'userId': 1, 'active': 1}")
@CompoundIndex(name = "device_active_idx", def = "{'deviceId': 1, 'active': 1}")
public class DeviceToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String userId;

    private String deviceId;

    private String platform;

    private String appVersion;

    private boolean active;

    private String deactivationReason;

    @Indexed(name = "deactivated_at_ttl_idx", expireAfter = "30d")
    private Instant deactivatedAt;

    private Instant createdAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }

    public void setDeactivationReason(String deactivationReason) {
        this.deactivationReason = deactivationReason;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(Instant deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
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
