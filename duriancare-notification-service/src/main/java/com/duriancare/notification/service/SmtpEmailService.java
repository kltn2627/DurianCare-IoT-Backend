package com.duriancare.notification.service;

import com.duriancare.notification.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "duriancare.notification.mail.enabled",
        havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final NotificationHistoryService historyService;

    public SmtpEmailService(
            JavaMailSender mailSender,
            MailProperties properties,
            NotificationHistoryService historyService) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.historyService = historyService;
    }

    @Override
    public void sendOtpEmail(String recipient, String otp, long ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(recipient);
            helper.setSubject("Ma xac nhan DurianCare");
            helper.setText(
                    "<h2>Ma OTP DurianCare</h2><p>Ma cua ban la: <strong>"
                            + otp
                            + "</strong></p><p>Ma co hieu luc trong "
                            + ttlMinutes
                            + " phut.</p>",
                    true);
            mailSender.send(message);
            historyService.recordOtpSent(recipient);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            historyService.recordOtpFailure(recipient, exception.getMessage());
            throw new EmailDeliveryException("Unable to deliver OTP email", exception);
        }
    }
}
