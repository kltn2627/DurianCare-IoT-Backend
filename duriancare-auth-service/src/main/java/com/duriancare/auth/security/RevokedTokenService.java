package com.duriancare.auth.security;

import com.duriancare.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RevokedTokenService {

    private static final String KEY_PREFIX = "duriancare:jwt:revoked:";

    private final StringRedisTemplate redisTemplate;

    public RevokedTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(Claims claims) {
        if (claims.getId() == null || claims.getExpiration() == null) {
            throw new InvalidTokenException("Token identifier and expiration are required");
        }
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            throw new InvalidTokenException("Token is already expired");
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + claims.getId(), "revoked", ttl);
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
    }
}
