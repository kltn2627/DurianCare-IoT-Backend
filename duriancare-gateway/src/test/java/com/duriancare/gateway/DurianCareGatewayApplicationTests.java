package com.duriancare.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "duriancare.security.jwt.secret=test-secret-with-at-least-32-characters",
        "spring.data.redis.password="
})
class DurianCareGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
