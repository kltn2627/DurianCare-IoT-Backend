package com.duriancare.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.notification.otp")
public record OtpProperties(Duration ttl, int length, int maxAttempts) {

    public OtpProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("OTP TTL must be positive");
        }
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("OTP length must be between 4 and 10");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("OTP max attempts must be positive");
        }
    }
}
