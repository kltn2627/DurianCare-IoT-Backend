package com.duriancare.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.gateway.security.JwtProperties;
import com.duriancare.gateway.security.JwtTokenValidator;
import com.duriancare.gateway.security.PublicEndpointMatcher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private ReactiveStringRedisTemplate redisTemplate;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        filter = new JwtAuthenticationFilter(
                new JwtTokenValidator(new JwtProperties(SECRET, "duriancare-auth-service")),
                new PublicEndpointMatcher(),
                redisTemplate);
    }

    @Test
    void clientSuppliedIdentityHeadersAreOverwrittenByJwtClaims() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/farms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("jwt-user", "FARMER", "jwt@example.com"))
                .header("X-Auth-User-Id", "spoofed-user")
                .header("X-Auth-Role", "ADMIN")
                .header("X-Auth-Email", "spoofed@example.com")
                .header("X-Internal-Token", "spoofed-service-token")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = next -> {
            forwarded.set(next);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Auth-User-Id")).isEqualTo("jwt-user");
        assertThat(headers.getFirst("X-Auth-Role")).isEqualTo("FARMER");
        assertThat(headers.getFirst("X-Auth-Email")).isEqualTo("jwt@example.com");
        assertThat(headers.containsKey("X-Internal-Token")).isFalse();
    }

    @Test
    void jwtClaimsWinOverSpoofedUserIdAndRole() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/farms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("engineer-1", "ENGINEER", "eng@example.com"))
                .header("X-Auth-User-Id", "owner-1")
                .header("X-Auth-Role", "FARMER")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Id")).isEqualTo("engineer-1");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-Role")).isEqualTo("ENGINEER");
    }

    @Test
    void missingJwtIsRejectedBeforeInternalIdentityIsForwarded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/farms")
                .header("X-Auth-User-Id", "spoofed-user")
                .header("X-Internal-Token", "spoofed-service-token")
                .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void revocationStoreFailureDoesNotBlockAuthenticatedRequestsByDefault() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("jwt-user", "FARMER", "jwt@example.com"))
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void revocationStoreFailureCanFailClosedWhenConfigured() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        JwtAuthenticationFilter failClosedFilter = new JwtAuthenticationFilter(
                new JwtTokenValidator(new JwtProperties(SECRET, "duriancare-auth-service")),
                new PublicEndpointMatcher(),
                redisTemplate,
                Duration.ofMillis(100),
                false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("jwt-user", "FARMER", "jwt@example.com"))
                .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(failClosedFilter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(exchange);
    }

    private String token(String subject, String role, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(subject + "-token")
                .issuer("duriancare-auth-service")
                .subject(subject)
                .claim("type", "access")
                .claim("role", role)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
