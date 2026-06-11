package com.duriancare.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({OtpProperties.class, MailProperties.class})
public class NotificationConfig {

    @Bean
    PasswordEncoder otpPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
