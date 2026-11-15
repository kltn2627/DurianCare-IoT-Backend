package com.duriancare.notification.service.push;

import java.time.Duration;

/**
 * @param errorCode  provider code for logs and the delivery record; null when accepted
 * @param retryAfter provider back-off hint, null when it gave none
 */
public record PushSendResult(Outcome outcome, String errorCode, Duration retryAfter) {

    public enum Outcome {
        /** FCM took the message. Says nothing about the phone. */
        ACCEPTED,
        /** The token is dead; retire it. */
        INVALID_TOKEN,
        /** Quota, outage, timeout: worth another bounded attempt. */
        RETRYABLE,
        /** Rejected in a way a retry cannot fix, and the token is not to blame. */
        PERMANENT
    }

    public static PushSendResult accepted() {
        return new PushSendResult(Outcome.ACCEPTED, null, null);
    }

    public static PushSendResult failed(Outcome outcome, String errorCode) {
        return new PushSendResult(outcome, errorCode, null);
    }
}
