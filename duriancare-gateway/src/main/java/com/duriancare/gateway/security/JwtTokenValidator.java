package com.duriancare.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenValidator {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN",
            "EXPERT",
            "FARMER",
            "GUEST");

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenValidator(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Claims validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Only access tokens are accepted by the gateway");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("Token subject is required");
        }
        if (!ALLOWED_ROLES.contains(claims.get("role", String.class))) {
            throw new IllegalArgumentException("Token role is not supported");
        }
        return claims;
    }
}
