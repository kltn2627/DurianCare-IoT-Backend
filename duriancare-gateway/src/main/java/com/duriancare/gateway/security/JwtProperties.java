package com.duriancare.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.security.jwt")
public record JwtProperties(String secret, String issuer) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        }
        issuer = issuer == null || issuer.isBlank() ? "duriancare-auth-service" : issuer;
    }
}
