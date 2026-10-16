package com.duriancare.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.auth")
public record AuthProperties(
        Duration otpTtl,
        int otpMaxAttempts,
        Duration otpResendCooldown,
        String eventsTopic) {

    public AuthProperties {
        if (otpTtl == null || otpTtl.isNegative() || otpTtl.isZero()) {
            throw new IllegalArgumentException("OTP TTL must be positive");
        }
        if (otpMaxAttempts < 1 || otpMaxAttempts > 10) {
            throw new IllegalArgumentException("OTP max attempts must be between 1 and 10");
        }
        if (otpResendCooldown == null
                || otpResendCooldown.isNegative()
                || otpResendCooldown.isZero()) {
            throw new IllegalArgumentException("OTP resend cooldown must be positive");
        }
        if (eventsTopic == null || eventsTopic.isBlank()) {
            throw new IllegalArgumentException("Auth events topic is required");
        }
    }
}
