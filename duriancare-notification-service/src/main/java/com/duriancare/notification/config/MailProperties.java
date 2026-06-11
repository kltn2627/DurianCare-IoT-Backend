package com.duriancare.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duriancare.notification.mail")
public record MailProperties(boolean enabled, String fromAddress, String fromName) {

    public MailProperties {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("Mail sender address is required");
        }
        if (fromName == null || fromName.isBlank()) {
            throw new IllegalArgumentException("Mail sender name is required");
        }
    }
}
