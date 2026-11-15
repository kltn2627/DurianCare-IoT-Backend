package com.duriancare.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.notification.service.push.PushGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class FcmConfigTest {

    private final FcmConfig config = new FcmConfig();

    private static FcmProperties properties(boolean enabled, String file, String base64) {
        return new FcmProperties(enabled, file, base64, null, null, null, null, 0, null, null, null, 0, 0);
    }

    /** A throwaway key in the service-account format; initialising the SDK does not contact Google. */
    private static String serviceAccountJson() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return new ObjectMapper().writeValueAsString(Map.of(
                "type", "service_account",
                "project_id", "duriancare-test",
                "private_key_id", "test",
                "private_key", "-----BEGIN PRIVATE KEY-----\n" + key + "\n-----END PRIVATE KEY-----\n",
                "client_email", "test@duriancare-test.iam.gserviceaccount.com",
                "client_id", "1",
                "token_uri", "https://oauth2.googleapis.com/token"));
    }

    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    void disabledByFlagNeedsNoCredentials() {
        assertThat(config.pushGateway(properties(false, null, null)).isEnabled()).isFalse();
    }

    @Test
    void enabledWithoutCredentialsTurnsPushOffInsteadOfFailingStartup() {
        assertThat(config.pushGateway(properties(true, null, null)).isEnabled()).isFalse();
    }

    @Test
    void missingCredentialFileTurnsPushOff() {
        assertThat(config.pushGateway(properties(true, "/does/not/exist.json", null)).isEnabled()).isFalse();
    }

    @Test
    void brokenCredentialsTurnPushOffWithoutLoggingThem(CapturedOutput output) {
        String secret = "c2VjcmV0LXZhbHVlLXRoYXQtbXVzdC1ub3QtYmUtbG9nZ2Vk";

        PushGateway gateway = config.pushGateway(properties(true, null, secret));

        assertThat(gateway.isEnabled()).isFalse();
        assertThat(output.getAll()).doesNotContain(secret).doesNotContain("secret-value");
    }

    @Test
    void loadsCredentialsFromAMountedFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("firebase.json");
        Files.writeString(file, serviceAccountJson());

        assertThat(config.pushGateway(properties(true, file.toString(), null)).isEnabled()).isTrue();
    }

    @Test
    void loadsCredentialsFromBase64() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(serviceAccountJson().getBytes(StandardCharsets.UTF_8));

        assertThat(config.pushGateway(properties(true, null, base64)).isEnabled()).isTrue();
    }
}
