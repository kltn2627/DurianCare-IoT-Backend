package com.duriancare.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.bootstrap.admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String fullName,
        String phoneNumber) {

    public AdminBootstrapProperties {
        if (enabled) {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Default admin email is required");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Default admin password is required");
            }
            if (fullName == null || fullName.isBlank()) {
                throw new IllegalArgumentException("Default admin full name is required");
            }
        }
    }
}
