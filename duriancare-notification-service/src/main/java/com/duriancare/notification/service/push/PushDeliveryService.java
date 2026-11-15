package com.duriancare.notification.service.push;

import com.duriancare.notification.config.FcmProperties;
import com.duriancare.notification.domain.DeviceToken;
import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.PushDelivery;
import com.duriancare.notification.domain.PushDeliveryStatus;
import com.duriancare.notification.repository.NotificationRepository;
import com.duriancare.notification.repository.PushDeliveryRepository;
import com.duriancare.notification.service.DeviceTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Takes a stored notification to FCM. The notification is already safe in the inbox before this runs, so
 * nothing here may fail the caller: {@link #enqueue} never throws and sending happens off the Kafka thread.
 *
 * <p>Exactly-one-sender comes from an atomic claim on the delivery row, so a redelivered Kafka event, the
 * retry scan and a second instance cannot send the same notification concurrently. Retries are bounded by
 * maxAttempts and only target tokens that failed transiently. If a sender dies mid-attempt, the stale claim
 * is retried to all tokens; the Android tag then replaces the earlier notification instead of stacking.
 */
@Service
public class PushDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushDeliveryService.class);
    private static final int SCAN_BATCH_SIZE = 100;

    private final MongoTemplate mongoTemplate;
    private final PushDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final DeviceTokenService deviceTokens;
    private final PushGateway gateway;
    private final PushMessageFactory messageFactory;
    private final FcmProperties properties;
    private final Executor pushExecutor;
    private final Clock clock;

    public PushDeliveryService(
            MongoTemplate mongoTemplate,
            PushDeliveryRepository deliveries,
            NotificationRepository notifications,
            DeviceTokenService deviceTokens,
            PushGateway gateway,
            PushMessageFactory messageFactory,
            FcmProperties properties,
            Executor pushExecutor,
            Clock pushClock) {
        this.mongoTemplate = mongoTemplate;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.deviceTokens = deviceTokens;
        this.gateway = gateway;
        this.messageFactory = messageFactory;
        this.properties = properties;
        this.pushExecutor = pushExecutor;
        this.clock = pushClock;
    }


    /**
     * Called for new notifications and for redelivered events alike: the latter repairs a crash between the
     * inbox write and this call, and the age limit keeps a Kafka replay from pushing old history.
     */
    public void enqueue(Notification notification) {
        if (!gateway.isEnabled() || notification == null || notification.getId() == null) {
            return;
        }
        try {
            if (isTooOld(notification)) {
                return;
            }
            createDeliveryIfAbsent(notification);
            submit(notification.getId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not queue push for notification {} ({})",
                    notification.getId(),
                    exception.getClass().getSimpleName());
        }
    }

    /** Picks up due retries, deliveries the executor rejected, and claims abandoned by a crash. */
    public void processDue() {
        if (!gateway.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        deliveries.findDue(now, now.minus(properties.claimTtl()),
                        PageRequest.of(0, SCAN_BATCH_SIZE))
                .forEach(delivery -> submit(delivery.getNotificationId()));
    }

    private void submit(String notificationId) {
        try {
            pushExecutor.execute(() -> process(notificationId));
        } catch (TaskRejectedException busy) {
            LOGGER.debug("Push executor is full; notification {} waits for the retry scan", notificationId);
        }
    }

    void process(String notificationId) {
        try {
            PushDelivery delivery = claim(notificationId);
            if (delivery != null) {
                attempt(delivery);
            }
        } catch (RuntimeException exception) {
            // The claim stays behind and the scan retries it once stale, still inside maxAttempts.
            LOGGER.warn(
                    "Push attempt for notification {} aborted ({})",
                    notificationId,
                    exception.getClass().getSimpleName());
        }
    }

    private void createDeliveryIfAbsent(Notification notification) {
        Instant now = clock.instant();
        PushDelivery delivery = new PushDelivery();
        delivery.setNotificationId(notification.getId());
        delivery.setReceiverId(notification.getReceiverId());
        delivery.setStatus(PushDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(now);
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        try {
            mongoTemplate.insert(delivery);
        } catch (DuplicateKeyException alreadyTracked) {
            LOGGER.debug("Push delivery for notification {} already exists", notification.getId());
        }
    }

    /** Null when another sender holds it, it is not due yet, it is finished, or attempts are used up. */
    private PushDelivery claim(String notificationId) {
        Instant now = clock.instant();
        Criteria due = new Criteria().orOperator(
                Criteria.where("status").in(PushDeliveryStatus.PENDING, PushDeliveryStatus.RETRY)
                        .and("nextAttemptAt").lte(now),
                Criteria.where("status").is(PushDeliveryStatus.SENDING)
                        .and("claimedAt").lte(now.minus(properties.claimTtl())));
        PushDelivery claimed = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(notificationId)
                        .and("attempts").lt(properties.maxAttempts())
                        .andOperator(due)),
                new Update()
                        .set("status", PushDeliveryStatus.SENDING)
                        .set("claimedAt", now)
                        .set("updatedAt", now)
                        .inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                PushDelivery.class);
        if (claimed == null) {
            abandonIfExhausted(notificationId, now);
        }
        return claimed;
    }

    /** A sender that crashed on its last allowed attempt leaves nothing that can claim the row again. */
    private void abandonIfExhausted(String notificationId, Instant now) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(notificationId)
                        .and("status").is(PushDeliveryStatus.SENDING)
                        .and("attempts").gte(properties.maxAttempts())
                        .and("claimedAt").lte(now.minus(properties.claimTtl()))),
                new Update()
                        .set("status", PushDeliveryStatus.FAILED)
                        .set("lastError", "SENDER_LOST")
                        .set("updatedAt", now),
                PushDelivery.class);
    }

    private void attempt(PushDelivery delivery) {
        Optional<Notification> found = notifications.findById(delivery.getNotificationId());
        if (found.isEmpty()) {
            finish(delivery, PushDeliveryStatus.CANCELLED, 0, 0, null, "NOTIFICATION_DELETED");
            return;
        }
        Notification notification = found.get();
        if (isTooOld(notification)) {
            finish(delivery, PushDeliveryStatus.EXPIRED, 0, 0, null, "TOO_OLD");
            return;
        }

        List<DeviceToken> targets = targets(delivery);
        if (targets.isEmpty()) {
            finish(delivery, delivery.getAcceptedCount() > 0 ? PushDeliveryStatus.SENT
                    : delivery.getRetryDeviceTokenIds() == null ? PushDeliveryStatus.NO_DEVICE
                    : PushDeliveryStatus.FAILED, 0, 0, null, delivery.getLastError());
            return;
        }

        List<PushSendResult> results = gateway.send(
                messageFactory.create(notification),
                targets.stream().map(DeviceToken::getToken).toList());
        apply(delivery, targets, results);
    }

    private List<DeviceToken> targets(PushDelivery delivery) {
        List<String> retryIds = delivery.getRetryDeviceTokenIds();
        return retryIds == null
                ? deviceTokens.activeTokens(delivery.getReceiverId())
                : deviceTokens.activeTokens(delivery.getReceiverId(), retryIds);
    }

    private void apply(PushDelivery delivery, List<DeviceToken> targets, List<PushSendResult> results) {
        int accepted = 0;
        List<String> invalid = new ArrayList<>();
        List<String> retry = new ArrayList<>();
        String lastError = null;
        Duration retryAfter = null;
        for (int index = 0; index < targets.size(); index++) {
            PushSendResult result = results.get(index);
            String tokenId = targets.get(index).getId();
            switch (result.outcome()) {
                case ACCEPTED -> accepted++;
                case INVALID_TOKEN -> invalid.add(tokenId);
                case RETRYABLE -> {
                    retry.add(tokenId);
                    if (result.retryAfter() != null) {
                        retryAfter = result.retryAfter();
                    }
                }
                case PERMANENT -> { }
            }
            if (result.errorCode() != null) {
                lastError = result.errorCode();
            }
        }
        deviceTokens.deactivateInvalid(invalid);

        if (!retry.isEmpty() && delivery.getAttempts() < properties.maxAttempts()) {
            scheduleRetry(delivery, accepted, invalid.size(), retry, lastError, retryAfter);
            return;
        }
        boolean nothingLeft = retry.isEmpty() && delivery.getAcceptedCount() + accepted > 0;
        finish(delivery, nothingLeft ? PushDeliveryStatus.SENT : PushDeliveryStatus.FAILED,
                accepted, invalid.size(), retry, lastError);
        LOGGER.info(
                "Push for notification {}: accepted={} invalid={} failed={} attempts={}",
                delivery.getNotificationId(),
                accepted,
                invalid.size(),
                targets.size() - accepted - invalid.size(),
                delivery.getAttempts());
    }

    private void scheduleRetry(
            PushDelivery delivery,
            int accepted,
            int invalid,
            List<String> retry,
            String lastError,
            Duration retryAfter) {
        Instant now = clock.instant();
        Duration delay = backoff(delivery.getAttempts());
        if (retryAfter != null && retryAfter.compareTo(delay) > 0) {
            delay = retryAfter;
        }
        update(delivery, new Update()
                .set("status", PushDeliveryStatus.RETRY)
                .set("nextAttemptAt", now.plus(delay))
                .set("retryDeviceTokenIds", retry)
                .set("lastError", lastError)
                .set("updatedAt", now)
                .inc("acceptedCount", accepted)
                .inc("invalidTokenCount", invalid));
        LOGGER.info(
                "Push for notification {} will retry {} token(s) in {}s ({})",
                delivery.getNotificationId(),
                retry.size(),
                delay.toSeconds(),
                lastError);
    }

    private Duration backoff(int attempts) {
        Duration delay = properties.retryBaseDelay().multipliedBy(1L << Math.min(attempts - 1, 16));
        return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
    }

    private void finish(
            PushDelivery delivery,
            PushDeliveryStatus status,
            int accepted,
            int invalid,
            List<String> unsent,
            String lastError) {
        Instant now = clock.instant();
        Update update = new Update()
                .set("status", status)
                .set("lastError", lastError)
                .set("updatedAt", now)
                .inc("acceptedCount", accepted)
                .inc("invalidTokenCount", invalid)
                .unset("nextAttemptAt")
                .set("retryDeviceTokenIds", unsent);
        if (status == PushDeliveryStatus.SENT) {
            update.set("sentAt", now);
        }
        update(delivery, update);
    }

    private void update(PushDelivery delivery, Update update) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(delivery.getNotificationId())
                        .and("status").is(PushDeliveryStatus.SENDING)
                        .and("attempts").is(delivery.getAttempts())),
                update,
                PushDelivery.class);
    }

    private boolean isTooOld(Notification notification) {
        Instant createdAt = notification.getCreatedAt();
        return createdAt != null && createdAt.isBefore(clock.instant().minus(properties.maxAge()));
    }
}
