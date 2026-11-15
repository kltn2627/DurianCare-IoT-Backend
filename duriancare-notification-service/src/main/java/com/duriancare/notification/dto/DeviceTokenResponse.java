package com.duriancare.notification.dto;

import com.duriancare.notification.domain.DeviceToken;
import java.time.Instant;

/** Never echoes the token itself. */
public record DeviceTokenResponse(String deviceId, String platform, boolean active, Instant updatedAt) {

    public static DeviceTokenResponse from(DeviceToken token) {
        return new DeviceTokenResponse(
                token.getDeviceId(),
                token.getPlatform(),
                token.isActive(),
                token.getUpdatedAt());
    }
}
