package com.duriancare.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.auth.config.JwtProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    @Test
    void accessTokenContainsGatewayRequiredClaims() {
        JwtService jwtService = new JwtService(new JwtProperties(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "duriancare-auth-service",
                Duration.ofHours(1),
                Duration.ofDays(7)));
        User user = new User(
                "expert@example.com",
                "password-hash",
                UserStatus.ACTIVE,
                UserRole.EXPERT);
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        IssuedToken token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseAccessToken(token.value());

        assertThat(claims.getIssuer()).isEqualTo("duriancare-auth-service");
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("email", String.class)).isEqualTo("expert@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("EXPERT");
    }
}
