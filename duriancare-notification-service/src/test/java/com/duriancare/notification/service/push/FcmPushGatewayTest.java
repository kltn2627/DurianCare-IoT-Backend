package com.duriancare.notification.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.notification.config.FcmProperties;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Firebase Admin SDK against a fake HTTP transport that answers with FCM v1 responses.
 * No network, no real credentials.
 */
class FcmPushGatewayTest {

    private static final PushMessage MESSAGE = new PushMessage(
            "noti-1", "Cảnh báo", "Độ ẩm đất thấp", Map.of("notificationId", "noti-1", "eventType", "SOIL_MOISTURE_LOW"));

    private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());
    private FirebaseApp app;

    private static FcmProperties properties() {
        return new FcmProperties(true, null, null, "demo", "duriancare-alerts",
                Duration.ofHours(1), Duration.ofHours(6), 5, null, null, null, 0, 0);
    }

    private static MockLowLevelHttpResponse fcmError(int status, String fcmCode, String message) {
        String grpcStatus = switch (status) {
            case 400 -> "INVALID_ARGUMENT";
            case 403 -> "PERMISSION_DENIED";
            case 404 -> "NOT_FOUND";
            case 429 -> "RESOURCE_EXHAUSTED";
            default -> "UNAVAILABLE";
        };
        return new MockLowLevelHttpResponse()
                .setStatusCode(status)
                .setContentType("application/json")
                .setContent("""
                        {"error":{"code":%d,"message":"%s","status":"%s","details":[{
                          "@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"%s"}]}}
                        """.formatted(status, message, grpcStatus, fcmCode));
    }

    private static MockLowLevelHttpResponse fcmOk() {
        return new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContentType("application/json")
                .setContent("{\"name\":\"projects/demo/messages/1\"}");
    }

    private FcmPushGateway gateway(Function<String, MockLowLevelHttpResponse> perToken) {
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() throws IOException {
                        String body = getContentAsString();
                        requestBodies.add(body);
                        for (String token : List.of("tok-ok", "tok-dead", "tok-quota", "tok-down", "tok-bad", "tok-payload")) {
                            if (body.contains("\"" + token + "\"")) {
                                return perToken.apply(token);
                            }
                        }
                        return fcmOk();
                    }
                };
            }
        };
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken("fake", new Date(System.currentTimeMillis() + 3_600_000)));
        app = FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId("demo")
                        .setHttpTransport(transport)
                        .build(),
                "test-" + UUID.randomUUID());
        return new FcmPushGateway(FirebaseMessaging.getInstance(app), properties());
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.delete();
        }
    }

    @Test
    void classifiesEachTokenInAMulticast() {
        FcmPushGateway gateway = gateway(token -> switch (token) {
            case "tok-dead" -> fcmError(404, "UNREGISTERED", "Requested entity was not found.");
            case "tok-quota" -> fcmError(429, "QUOTA_EXCEEDED", "Quota exceeded");
            case "tok-down" -> fcmError(403, "SENDER_ID_MISMATCH", "SenderId mismatch");
            case "tok-bad" -> fcmError(400, "INVALID_ARGUMENT", "The registration token is not a valid FCM registration token");
            case "tok-payload" -> fcmError(400, "INVALID_ARGUMENT", "Invalid value at 'message.data'");
            default -> fcmOk();
        });

        List<PushSendResult> results = gateway.send(MESSAGE, List.of("tok-ok", "tok-dead", "tok-quota", "tok-down", "tok-bad", "tok-payload"));

        assertThat(results).extracting(PushSendResult::outcome).containsExactly(
                PushSendResult.Outcome.ACCEPTED,
                PushSendResult.Outcome.INVALID_TOKEN,
                PushSendResult.Outcome.RETRYABLE,
                PushSendResult.Outcome.INVALID_TOKEN,
                PushSendResult.Outcome.INVALID_TOKEN,
                PushSendResult.Outcome.PERMANENT);
        assertThat(results.get(2).errorCode()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    void sendsNotificationDataAndAndroidOptions() {
        FcmPushGateway gateway = gateway(token -> fcmOk());

        gateway.send(MESSAGE, List.of("tok-ok"));

        assertThat(requestBodies).hasSize(1);
        assertThat(requestBodies.get(0))
                .contains("\"title\":\"Cảnh báo\"")
                .contains("\"notificationId\":\"noti-1\"")
                .contains("\"tag\":\"noti-1\"")
                .contains("\"channel_id\":\"duriancare-alerts\"")
                .contains("\"priority\":\"high\"")
                .contains("\"ttl\":\"3600s\"");
    }

    @Test
    void splitsLargeRecipientListsIntoBatchesAndKeepsOrder() {
        FcmPushGateway gateway = gateway(token -> fcmOk());
        List<String> tokens = IntStream.range(0, FcmPushGateway.MAX_TOKENS_PER_BATCH + 3).mapToObj(i -> "token-" + i).toList();

        List<PushSendResult> results = gateway.send(MESSAGE, tokens);

        assertThat(results).hasSize(tokens.size());
        assertThat(requestBodies).hasSize(tokens.size());
    }
}
