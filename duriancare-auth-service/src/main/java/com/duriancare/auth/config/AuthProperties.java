package com.duriancare.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.auth")
public record AuthProperties(Duration otpTtl, String eventsTopic) {

    public AuthProperties {
        if (otpTtl == null || otpTtl.isNegative() || otpTtl.isZero()) {
            throw new IllegalArgumentException("OTP TTL must be positive");
        }
        if (eventsTopic == null || eventsTopic.isBlank()) {
            throw new IllegalArgumentException("Auth events topic is required");
        }
    }
}
