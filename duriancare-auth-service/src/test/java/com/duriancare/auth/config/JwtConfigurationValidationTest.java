package com.duriancare.auth.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtConfigurationValidationTest {
    @Test void whitespaceOnlySecretMustNotStartAuthService() {
        assertThatThrownBy(() -> new JwtProperties(" ".repeat(64), "issuer", Duration.ofHours(1), Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
