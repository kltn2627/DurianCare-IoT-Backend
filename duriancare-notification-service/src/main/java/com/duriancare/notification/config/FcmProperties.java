package com.duriancare.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param ttl       how long FCM keeps a message for an offline phone
 * @param maxAge    a notification older than this is no longer pushed (replays, long outages)
 * @param claimTtl  how long a SENDING claim is honoured before it counts as a crashed sender
 */
@ConfigurationProperties(prefix = "duriancare.notification.fcm")
public record FcmProperties(
        boolean enabled,
        String credentialsFile,
        String credentialsBase64,
        String projectId,
        String androidChannelId,
        Duration ttl,
        Duration maxAge,
        int maxAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay,
        Duration claimTtl,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public FcmProperties {
        if (androidChannelId == null || androidChannelId.isBlank()) {
            androidChannelId = "duriancare-alerts";
        }
        ttl = ttl == null ? Duration.ofHours(6) : ttl;
        maxAge = maxAge == null ? Duration.ofHours(6) : maxAge;
        maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
        retryBaseDelay = retryBaseDelay == null ? Duration.ofSeconds(30) : retryBaseDelay;
        retryMaxDelay = retryMaxDelay == null ? Duration.ofMinutes(15) : retryMaxDelay;
        claimTtl = claimTtl == null ? Duration.ofMinutes(5) : claimTtl;
        connectTimeoutMs = connectTimeoutMs <= 0 ? 10_000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs <= 0 ? 15_000 : readTimeoutMs;
    }
}
