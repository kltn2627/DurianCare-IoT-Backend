package com.duriancare.gateway.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class PublicEndpointMatcher {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/register/engineer",
            "/api/auth/otp/resend",
            "/api/auth/otp/verify",
            "/api/auth/refresh",
            "/api/v1/notification/otp/generate",
            "/api/v1/notification/otp/validate",
            "/actuator/health",
            "/actuator/info",
            "/**/v3/api-docs/**",
            "/swagger-ui/**");

    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/knowledge/articles",
            "/api/knowledge/articles/*",
            "/api/knowledge/articles/*/related",
            "/api/knowledge/categories",
            "/api/knowledge/category-options",
            "/api/knowledge/images/*");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean matches(HttpMethod method, String path) {
        if (PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return true;
        }
        return HttpMethod.GET.equals(method)
                && PUBLIC_GET_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
