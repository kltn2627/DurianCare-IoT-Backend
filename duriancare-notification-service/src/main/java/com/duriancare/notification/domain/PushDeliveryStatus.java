package com.duriancare.notification.domain;

/**
 * State of the FCM hand-off for one stored notification. SENT means FCM accepted the message for at least
 * one device and nothing is left to retry; it does not mean a phone displayed it (FCM gives the Admin SDK no
 * per-device delivery receipt).
 */
public enum PushDeliveryStatus {
    PENDING,
    SENDING,
    RETRY,
    SENT,
    NO_DEVICE,
    FAILED,
    EXPIRED,
    CANCELLED
}
