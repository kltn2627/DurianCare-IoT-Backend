package com.duriancare.notification.service.push;

import java.util.List;

/** Hands one message to the push provider. */
public interface PushGateway {

    boolean isEnabled();

    /** Returns one result per token, in order. Provider failures are results, not exceptions. */
    List<PushSendResult> send(PushMessage message, List<String> tokens);
}
