package com.duriancare.auth.security;

import com.duriancare.auth.exception.AuthenticationFailedException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenSessionService {

    private static final String TOKEN_PREFIX = "duriancare:jwt:refresh:";
    private static final String USER_PREFIX = "duriancare:jwt:refresh:user:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void register(UUID userId, IssuedToken refreshToken) {
        Duration ttl = remainingTtl(refreshToken.expiresAt());
        String tokenKey = TOKEN_PREFIX + refreshToken.tokenId();
        String userKey = USER_PREFIX + userId;
        redisTemplate.opsForValue().set(tokenKey, userId.toString(), ttl);
        redisTemplate.opsForSet().add(userKey, refreshToken.tokenId());
        redisTemplate.expire(userKey, ttl);
    }

    public void consume(UUID userId, String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            throw new AuthenticationFailedException("Refresh token identifier is missing");
        }
        String storedUserId = redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + tokenId);
        redisTemplate.opsForSet().remove(USER_PREFIX + userId, tokenId);
        if (!userId.toString().equals(storedUserId)) {
            throw new AuthenticationFailedException("Refresh token is expired, revoked, or already used");
        }
    }

    public void revokeAll(UUID userId) {
        String userKey = USER_PREFIX + userId;
        Set<String> tokenIds = redisTemplate.opsForSet().members(userKey);
        if (tokenIds != null && !tokenIds.isEmpty()) {
            Set<String> tokenKeys = new HashSet<>();
            for (String tokenId : tokenIds) {
                tokenKeys.add(TOKEN_PREFIX + tokenId);
            }
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(userKey);
    }

    private Duration remainingTtl(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new AuthenticationFailedException("Refresh token is already expired");
        }
        return ttl;
    }
}
