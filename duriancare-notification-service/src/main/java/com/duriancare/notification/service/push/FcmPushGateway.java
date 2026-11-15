package com.duriancare.notification.service.push;

import com.duriancare.notification.config.FcmProperties;
import com.google.firebase.ErrorCode;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FcmPushGateway implements PushGateway {

    /** FCM's limit for one multicast. */
    static final int MAX_TOKENS_PER_BATCH = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcmPushGateway.class);

    private final FirebaseMessaging messaging;
    private final FcmProperties properties;

    public FcmPushGateway(FirebaseMessaging messaging, FcmProperties properties) {
        this.messaging = messaging;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<PushSendResult> send(PushMessage message, List<String> tokens) {
        List<PushSendResult> results = new ArrayList<>(tokens.size());
        for (int start = 0; start < tokens.size(); start += MAX_TOKENS_PER_BATCH) {
            results.addAll(sendBatch(message, tokens.subList(start, Math.min(start + MAX_TOKENS_PER_BATCH, tokens.size()))));
        }
        return results;
    }

    private List<PushSendResult> sendBatch(PushMessage message, List<String> tokens) {
        try {
            BatchResponse response = messaging.sendEachForMulticast(multicast(message, tokens));
            return response.getResponses().stream().map(this::toResult).toList();
        } catch (FirebaseMessagingException exception) {
            return Collections.nCopies(tokens.size(), classify(exception));
        } catch (RuntimeException exception) {
            LOGGER.warn("FCM send failed unexpectedly ({})", exception.getClass().getSimpleName());
            return Collections.nCopies(
                    tokens.size(),
                    PushSendResult.failed(PushSendResult.Outcome.RETRYABLE, exception.getClass().getSimpleName()));
        }
    }

    private MulticastMessage multicast(PushMessage message, List<String> tokens) {
        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(properties.ttl().toMillis())
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(properties.androidChannelId())
                                .setTag(message.notificationId())
                                .build())
                        .build())
                .build();
    }

    private PushSendResult toResult(SendResponse response) {
        if (response.isSuccessful()) {
            return PushSendResult.accepted();
        }
        return classify(response.getException());
    }

    private PushSendResult classify(FirebaseMessagingException exception) {
        MessagingErrorCode code = exception.getMessagingErrorCode();
        ErrorCode generic = exception.getErrorCode();
        String label = code != null ? code.name() : generic != null ? generic.name() : "UNKNOWN";
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return PushSendResult.failed(PushSendResult.Outcome.INVALID_TOKEN, label);
        }
        if (code == MessagingErrorCode.INVALID_ARGUMENT) {
            // INVALID_ARGUMENT also covers a bad payload, and treating that as a bad token would wipe
            // every device of the receiver. Only FCM's own wording blames the token.
            return PushSendResult.failed(blamesToken(exception)
                    ? PushSendResult.Outcome.INVALID_TOKEN
                    : PushSendResult.Outcome.PERMANENT, label);
        }
        if (code == MessagingErrorCode.THIRD_PARTY_AUTH_ERROR) {
            return PushSendResult.failed(PushSendResult.Outcome.PERMANENT, label);
        }
        return new PushSendResult(PushSendResult.Outcome.RETRYABLE, label, retryAfter(exception));
    }

    private boolean blamesToken(FirebaseMessagingException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("registration token");
    }

    private Duration retryAfter(FirebaseMessagingException exception) {
        IncomingHttpResponse response = exception.getHttpResponse();
        if (response == null || response.getHeaders() == null) {
            return null;
        }
        Object value = response.getHeaders().get("retry-after");
        if (value instanceof List<?> list && !list.isEmpty()) {
            value = list.get(0);
        }
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.toString().trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
