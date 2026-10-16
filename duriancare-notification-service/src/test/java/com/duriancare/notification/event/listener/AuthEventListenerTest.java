package com.duriancare.notification.event.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.duriancare.notification.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthEventListenerTest {

    @Test
    void malformedOtpEventDoesNotLeakPayloadIntoLogs() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AuthEventListener.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        boolean previous = logger.isAdditive();
        logger.setAdditive(false);
        try {
            listener.handle("{\"eventType\":\"USER_REGISTERED\",\"email\":\"fixture@example.invalid\",\"otpCode\":\"fixture-sensitive\",\"expiresInMinutes\":0}");
            org.assertj.core.api.Assertions.assertThat(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("otpCode"))).isFalse();
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(previous);
            appender.stop();
        }
    }

    @Mock
    private EmailService emailService;

    private AuthEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuthEventListener(new ObjectMapper(), emailService);
    }

    @Test
    void sendsOtpEmailForSupportedEvent() throws Exception {
        listener.handle("""
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "USER_REGISTERED",
                  "email": "user@example.com",
                  "fullName": "Durian Farmer",
                  "otpCode": "123456",
                  "expiresInMinutes": 5
                }
                """);

        verify(emailService).sendOtpEmail("user@example.com", "123456", 5);
    }

    @Test
    void ignoresMalformedPayloadWithoutThrowing() {
        assertThatCode(() -> listener.handle("{broken")).doesNotThrowAnyException();
        verifyNoInteractions(emailService);
    }

    @Test
    void deliveryFailureMustReachKafkaRetryWithoutLeakingProviderDetails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture-provider-sensitive-detail"))
                .when(emailService).sendOtpEmail("fixture@example.invalid", "fixture-code", 5);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.handle(
                "{\"eventType\":\"USER_REGISTERED\",\"email\":\"fixture@example.invalid\",\"otpCode\":\"fixture-code\",\"expiresInMinutes\":5}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authentication event delivery failed")
                .hasNoCause();
    }
}
