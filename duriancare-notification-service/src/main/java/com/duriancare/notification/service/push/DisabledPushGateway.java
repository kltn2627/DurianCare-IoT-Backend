package com.duriancare.notification.service.push;

import java.util.List;

public class DisabledPushGateway implements PushGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public List<PushSendResult> send(PushMessage message, List<String> tokens) {
        throw new IllegalStateException("FCM push is disabled");
    }
}
