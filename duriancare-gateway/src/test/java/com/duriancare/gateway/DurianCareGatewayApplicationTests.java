package com.duriancare.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "duriancare.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.data.redis.password="
})
class DurianCareGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
