package com.duriancare.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AuthRateLimitFilterTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/api/v1/notification/otp/generate", "/api/v1/notification/otp/validate"})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void notificationOtpAliasesCannotBypassRateLimiting(String path) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(Flux.just(100L));
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(100L));
        var filter = new AuthRateLimitFilter(redis, 1, 1, 1, 1, 1, 100);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(reactor.core.publisher.Mono.empty());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, never()).filter(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void redisTimeoutReturnsServiceUnavailableInsteadOfHangingAuthPosts() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Flux.never());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.never());
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                redisTemplate,
                10,
                5,
                10,
                3,
                30,
                100);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register/engineer").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(exchange);
    }
}
