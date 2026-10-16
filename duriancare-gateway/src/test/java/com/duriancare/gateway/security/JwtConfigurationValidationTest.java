package com.duriancare.gateway.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class JwtConfigurationValidationTest {
    @Test void whitespaceOnlySecretMustNotStartGateway() {
        assertThatThrownBy(() -> new JwtProperties(" ".repeat(64), "issuer"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
