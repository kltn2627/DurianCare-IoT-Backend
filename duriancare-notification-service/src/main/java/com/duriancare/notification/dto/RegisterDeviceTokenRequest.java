package com.duriancare.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The owner is always the authenticated caller, so this body deliberately has no userId. */
public record RegisterDeviceTokenRequest(
        @NotBlank(message = "token is required")
        @Size(min = 32, max = 4096, message = "token has an invalid length")
        @Pattern(regexp = "^[A-Za-z0-9_:.\\-]+$", message = "token is not an FCM registration token")
        String token,
        @NotBlank(message = "deviceId is required")
        @Pattern(regexp = "^[A-Za-z0-9_\\-]{8,128}$", message = "deviceId is invalid")
        String deviceId,
        @NotBlank(message = "platform is required")
        @Pattern(regexp = "^ANDROID$", message = "platform must be ANDROID")
        String platform,
        @Size(max = 32, message = "appVersion is too long")
        String appVersion) {

    /** Keeps the raw token out of accidental logging of the request. */
    @Override
    public String toString() {
        return "RegisterDeviceTokenRequest[deviceId=" + deviceId + ", platform=" + platform + "]";
    }
}
