package com.duriancare.notification.config;

import com.duriancare.notification.service.push.DisabledPushGateway;
import com.duriancare.notification.service.push.FcmPushGateway;
import com.duriancare.notification.service.push.PushGateway;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(FcmProperties.class)
public class FcmConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcmConfig.class);
    private static final String FIREBASE_APP_NAME = "duriancare-notification";

    @Bean
    Clock pushClock() {
        return Clock.systemUTC();
    }

    /** Sending never runs on the Kafka listener thread; a full queue leaves the delivery to the retry scan. */
    @Bean
    ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("fcm-push-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        return executor;
    }

    /**
     * A missing or broken Firebase setup must not stop the service: inbox, email and Kafka keep working and
     * only push is off. The failure log names the exception type only, because credential parsers can quote
     * the input they rejected.
     */
    @Bean
    PushGateway pushGateway(FcmProperties properties) {
        if (!properties.enabled()) {
            LOGGER.info("FCM push is disabled (FCM_ENABLED=false)");
            return new DisabledPushGateway();
        }
        try {
            FirebaseApp app = firebaseApp(properties);
            LOGGER.info("FCM push is enabled for project {}", app.getOptions().getProjectId());
            return new FcmPushGateway(FirebaseMessaging.getInstance(app), properties);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error(
                    "FCM push is disabled: Firebase could not be initialised ({})",
                    exception.getClass().getSimpleName());
            return new DisabledPushGateway();
        }
    }

    private FirebaseApp firebaseApp(FcmProperties properties) throws IOException {
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FIREBASE_APP_NAME.equals(app.getName())) {
                return app;
            }
        }
        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(credentials(properties))
                .setConnectTimeout(properties.connectTimeoutMs())
                .setReadTimeout(properties.readTimeoutMs());
        if (StringUtils.hasText(properties.projectId())) {
            options.setProjectId(properties.projectId().trim());
        }
        return FirebaseApp.initializeApp(options.build(), FIREBASE_APP_NAME);
    }

    private GoogleCredentials credentials(FcmProperties properties) throws IOException {
        if (StringUtils.hasText(properties.credentialsFile())) {
            try (InputStream stream = Files.newInputStream(Path.of(properties.credentialsFile().trim()))) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        if (StringUtils.hasText(properties.credentialsBase64())) {
            byte[] json = Base64.getDecoder().decode(properties.credentialsBase64().trim());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        }
        throw new IllegalStateException("No Firebase credentials configured");
    }
}
