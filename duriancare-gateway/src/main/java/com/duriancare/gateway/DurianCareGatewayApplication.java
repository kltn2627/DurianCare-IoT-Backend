package com.duriancare.gateway;

import com.duriancare.gateway.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class DurianCareGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DurianCareGatewayApplication.class, args);
    }
}
