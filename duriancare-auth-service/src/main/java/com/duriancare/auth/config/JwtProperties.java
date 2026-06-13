package com.duriancare.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
    }
}
