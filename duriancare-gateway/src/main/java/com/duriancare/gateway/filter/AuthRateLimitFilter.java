package com.duriancare.gateway.filter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Map<String, RateLimitRule> rules;

    public AuthRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${duriancare.security.rate-limit.login-limit:10}") int loginLimit,
            @Value("${duriancare.security.rate-limit.registration-limit:5}") int registrationLimit,
            @Value("${duriancare.security.rate-limit.otp-verification-limit:10}") int otpVerificationLimit,
            @Value("${duriancare.security.rate-limit.otp-resend-limit:3}") int otpResendLimit,
            @Value("${duriancare.security.rate-limit.refresh-limit:30}") int refreshLimit) {
        this.redisTemplate = redisTemplate;
        this.rules = Map.of(
                "/api/auth/login", new RateLimitRule(loginLimit, 60),
                "/api/auth/register", new RateLimitRule(registrationLimit, 600),
                "/api/auth/otp/verify", new RateLimitRule(otpVerificationLimit, 300),
                "/api/auth/otp/resend", new RateLimitRule(otpResendLimit, 600),
                "/api/auth/refresh", new RateLimitRule(refreshLimit, 60));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!"POST".equals(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        RateLimitRule rule = rules.get(path);
        if (rule == null) {
            return chain.filter(exchange);
        }

        String key = "duriancare:rate-limit:auth:"
                + path.replace('/', ':')
                + ":"
                + clientAddress(exchange);
        return redisTemplate.execute(
                        INCREMENT_SCRIPT,
                        List.of(key),
                        String.valueOf(rule.windowSeconds()))
                .next()
                .onErrorResume(exception -> serviceUnavailable(exchange).thenReturn(-1L))
                .switchIfEmpty(serviceUnavailable(exchange).thenReturn(-1L))
                .flatMap(count -> {
                    if (count < 0) {
                        return Mono.empty();
                    }
                    return count > rule.limit()
                            ? tooManyRequests(exchange, rule.windowSeconds())
                            : chain.filter(exchange);
                });
    }

    private String clientAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, int retryAfterSeconds) {
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
        return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Too many authentication requests");
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Authentication protection is unavailable");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        byte[] body = ("{\"timestamp\":\""
                + Instant.now()
                + "\",\"status\":"
                + status.value()
                + ",\"error\":\""
                + status.getReasonPhrase()
                + "\",\"message\":\""
                + message
                + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -200;
    }

    private record RateLimitRule(int limit, int windowSeconds) {
    }
}
