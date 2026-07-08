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
}
