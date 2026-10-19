package com.duriancare.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.duriancare.gateway.security.JwtTokenValidator;
import com.duriancare.gateway.security.PublicEndpointMatcher;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CameraRegistrationRoutingTest {
    @Test
    void publicDeviceAnnouncementsPreserveCameraKeyWithoutFarmerJwt() {
        var filter = new JwtAuthenticationFilter(mock(JwtTokenValidator.class),
                new PublicEndpointMatcher(), mock(ReactiveStringRedisTemplate.class));
        String key = UUID.randomUUID().toString();
        for (String route : new String[]{"register", "heartbeat"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .post("/api/v1/public/cameras/" + route)
                    .header("X-Camera-Key", key).body("{\"cameraId\":\"fixture\"}"));
            var forwarded = new AtomicReference<ServerWebExchange>();
            StepVerifier.create(filter.filter(exchange, next -> {
                forwarded.set(next);
                return Mono.empty();
            })).verifyComplete();
            assertThat(forwarded.get()).isNotNull();
            assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Camera-Key")).isEqualTo(key);
            StepVerifier.create(org.springframework.core.io.buffer.DataBufferUtils
                    .join(forwarded.get().getRequest().getBody()).map(buffer -> {
                        String body = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
                        org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                        return body;
                    })).expectNext("{\"cameraId\":\"fixture\"}").verifyComplete();
        }
    }

    @Test
    void privateCameraAccessStillRequiresFarmerJwt() {
        var filter = new JwtAuthenticationFilter(mock(JwtTokenValidator.class),
                new PublicEndpointMatcher(), mock(ReactiveStringRedisTemplate.class));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/camera/devices").build());
        StepVerifier.create(filter.filter(exchange, next -> Mono.error(new AssertionError("Must not forward"))))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
