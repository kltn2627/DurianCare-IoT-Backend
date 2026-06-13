package com.duriancare.auth.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        String eventType,
        String email,
        String fullName,
        String otpCode,
        long expiresInMinutes,
        Instant occurredAt) {

    public static UserRegisteredEvent registration(
            String email,
            String fullName,
            String otpCode,
            long expiresInMinutes) {
        return create("USER_REGISTERED", email, fullName, otpCode, expiresInMinutes);
    }

    public static UserRegisteredEvent otpResent(
            String email,
            String fullName,
            String otpCode,
            long expiresInMinutes) {
        return create("REGISTRATION_OTP_RESENT", email, fullName, otpCode, expiresInMinutes);
    }

    private static UserRegisteredEvent create(
            String eventType,
            String email,
            String fullName,
            String otpCode,
            long expiresInMinutes) {
        return new UserRegisteredEvent(
                UUID.randomUUID(),
                eventType,
                email,
                fullName,
                otpCode,
                expiresInMinutes,
                Instant.now());
    }
}
