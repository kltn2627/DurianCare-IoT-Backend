package com.duriancare.auth.security;

import com.duriancare.auth.config.JwtProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken generateAccessToken(User user) {
        return generateToken(user, "access", properties.accessTokenTtl());
    }

    public IssuedToken generateRefreshToken(User user) {
        return generateToken(user, "refresh", properties.refreshTokenTtl());
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        requireTokenType(claims, "access");
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token);
        requireTokenType(claims, "refresh");
        return claims;
    }

    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        try {
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    UserRole.valueOf(claims.get("role", String.class)),
                    claims.getId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidTokenException("Token claims are invalid", exception);
        }
    }

    private IssuedToken generateToken(User user, String type, java.time.Duration ttl) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("type", type)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("Token is invalid or expired", exception);
        }
    }

    private void requireTokenType(Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new InvalidTokenException("Unexpected token type");
        }
    }
}
