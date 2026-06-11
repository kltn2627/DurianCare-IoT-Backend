package com.duriancare.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "duriancare.notification.mail.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledEmailService implements EmailService {

    @Override
    public void sendOtpEmail(String recipient, String otp, long ttlMinutes) {
        throw new EmailDeliveryException(
                "Email delivery is disabled",
                new IllegalStateException("MAIL_ENABLED=false"));
    }
}
