package com.duriancare.gateway.security;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class PublicEndpointMatcher {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/otp/resend",
            "/api/auth/otp/verify",
            "/api/auth/refresh",
            "/api/v1/notification/otp/generate",
            "/api/v1/notification/otp/validate",
            "/actuator/health",
            "/actuator/info",
            "/**/v3/api-docs/**",
            "/swagger-ui/**");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean matches(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
