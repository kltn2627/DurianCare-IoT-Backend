package com.duriancare.notification.controller;

import com.duriancare.notification.dto.DeviceTokenResponse;
import com.duriancare.notification.dto.RegisterDeviceTokenRequest;
import com.duriancare.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "Notification Device Tokens", description = "FCM device registration for the authenticated user")
@RequestMapping("/api/v1/notification/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PutMapping
    @Operation(summary = "Register device token", description = "Create or refresh the FCM token of this installation for the current user.")
    public DeviceTokenResponse register(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        return deviceTokenService.register(currentUserId, request);
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unregister device", description = "Stop push delivery to this installation for the current user.")
    public void unregister(
            @RequestHeader("X-Auth-User-Id") @NotBlank String currentUserId,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_\\-]{8,128}$", message = "deviceId is invalid") String deviceId) {
        deviceTokenService.unregister(currentUserId, deviceId);
    }
}
