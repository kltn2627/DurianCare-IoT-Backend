package com.duriancare.notification.service.push;

import java.util.Map;

/**
 * @param notificationId also the Android notification tag, so a re-send replaces rather than stacks
 * @param data           flat string map delivered to the app; see {@link PushMessageFactory}
 */
public record PushMessage(String notificationId, String title, String body, Map<String, String> data) {
}
