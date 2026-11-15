package com.duriancare.notification.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PushRetryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushRetryScheduler.class);

    private final PushDeliveryService deliveryService;

    public PushRetryScheduler(PushDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${duriancare.notification.fcm.retry-scan-interval:PT30S}")
    void scan() {
        try {
            deliveryService.processDue();
        } catch (RuntimeException exception) {
            LOGGER.warn("Push retry scan failed ({})", exception.getClass().getSimpleName());
        }
    }
}
