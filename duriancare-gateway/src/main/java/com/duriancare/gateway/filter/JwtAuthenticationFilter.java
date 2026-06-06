package com.duriancare.gateway.filter;

import com.duriancare.gateway.security.JwtTokenValidator;
import com.duriancare.gateway.security.PublicEndpointMatcher;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtTokenValidator tokenValidator;
    private final PublicEndpointMatcher publicEndpointMatcher;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(
            JwtTokenValidator tokenValidator,
            PublicEndpointMatcher publicEndpointMatcher,
            ReactiveStringRedisTemplate redisTemplate) {
        this.tokenValidator = tokenValidator;
        this.publicEndpointMatcher = publicEndpointMatcher;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (publicEndpointMatcher.matches(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing bearer token");
        }

        Claims claims;
        try {
            claims = tokenValidator.validate(header.substring(7));
        } catch (RuntimeException exception) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        String tokenId = claims.getId();
        if (tokenId == null || tokenId.isBlank()) {
            return unauthorized(exchange, "Token identifier is missing");
        }

        return redisTemplate.hasKey("duriancare:jwt:revoked:" + tokenId)
                .flatMap(revoked -> revoked
                        ? unauthorized(exchange, "Token has been revoked")
                        : forward(exchange, chain, claims))
                .onErrorResume(exception -> serviceUnavailable(exchange));
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, Claims claims) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-Auth-User-Id");
                    headers.remove("X-Auth-Email");
                    headers.remove("X-Auth-Role");
                    headers.set("X-Auth-User-Id", claims.getSubject());
                    headers.set("X-Auth-Email", valueOrEmpty(claims.get("email", String.class)));
                    headers.set("X-Auth-Role", valueOrEmpty(claims.get("role", String.class)));
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Token revocation store is unavailable");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
