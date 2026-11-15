package com.duriancare.notification.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.notification.config.FcmProperties;
import com.duriancare.notification.domain.DeviceToken;
import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationStatus;
import com.duriancare.notification.domain.PushDelivery;
import com.duriancare.notification.domain.PushDeliveryStatus;
import com.duriancare.notification.dto.CreateNotificationRequest;
import com.duriancare.notification.domain.NotificationType;
import com.duriancare.notification.dto.RegisterDeviceTokenRequest;
import com.duriancare.notification.event.listener.NotificationEventListener;
import com.duriancare.notification.mapper.NotificationMapper;
import com.duriancare.notification.repository.DeviceTokenRepository;
import com.duriancare.notification.repository.NotificationRepository;
import com.duriancare.notification.repository.PushDeliveryRepository;
import com.duriancare.notification.service.DeviceTokenService;
import com.duriancare.notification.service.impl.NotificationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

/**
 * Kafka payload to push state against a real MongoDB, using a throwaway database per test (same guard as
 * NotificationBulkMongoIntegrationTest). Only the provider is faked: a {@link FakeGateway} records what
 * would have gone to FCM. Set DURIANCARE_FIXTURE_MONGO_URI to run.
 */
@EnabledIfEnvironmentVariable(named = "DURIANCARE_FIXTURE_MONGO_URI", matches = ".+")
class PushFlowMongoIntegrationTest {

    private static final Pattern FIXTURE_DATABASE = Pattern.compile("notification_fixture_[0-9a-f]{32}");
    private static final String TOKEN_A = "token-a-0123456789012345678901234567890123";
    private static final String TOKEN_B = "token-b-0123456789012345678901234567890123";

    private final String database = "notification_fixture_" + UUID.randomUUID().toString().replace("-", "");
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
    private final FakeGateway gateway = new FakeGateway();
    private final Executor inline = Runnable::run;

    private MongoClient client;
    private boolean ownsDatabase;
    private MongoTemplate template;
    private NotificationRepository notifications;
    private PushDeliveryRepository deliveries;
    private DeviceTokenRepository tokens;
    private DeviceTokenService deviceTokens;
    private PushDeliveryService pushService;
    private NotificationServiceImpl notificationService;
    private NotificationEventListener listener;
    private Executor executor = inline;

    private static FcmProperties properties() {
        return new FcmProperties(true, null, null, "demo", "duriancare-alerts",
                Duration.ofHours(1), Duration.ofHours(6), 3,
                Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofMinutes(5), 0, 0);
    }

    private static String event(String sourceEventId, String receiver) {
        return """
                {"sourceEventId":"%s","eventType":"SOIL_MOISTURE_LOW","receiverId":"%s","title":"Cảnh báo",
                 "message":"Độ ẩm đất thấp","notificationType":"DEVICE","occurredAt":"2026-11-04T00:00:00Z",
                 "metadata":{"alertId":7,"deviceUid":"esp32-1","peerPhoneNumber":"0901234567"}}
                """.formatted(sourceEventId, receiver);
    }

    private static RegisterDeviceTokenRequest registration(String token, String deviceId) {
        return new RegisterDeviceTokenRequest(token, deviceId, "ANDROID", "1.0.0");
    }

    @BeforeEach
    void setup() {
        assertThat(database).matches(FIXTURE_DATABASE);
        client = MongoClients.create(System.getenv("DURIANCARE_FIXTURE_MONGO_URI"));
        assertThat(client.listDatabaseNames().into(new ArrayList<>())).doesNotContain(database);
        ownsDatabase = true;
        template = new MongoTemplate(client, database);
        template.indexOps(DeviceToken.class).ensureIndex(new Index("token", Sort.Direction.ASC).unique());
        template.indexOps(Notification.class).ensureIndex(new Index("sourceEventId", Sort.Direction.ASC).unique().sparse());
        MongoRepositoryFactory factory = new MongoRepositoryFactory(template);
        notifications = factory.getRepository(NotificationRepository.class);
        deliveries = factory.getRepository(PushDeliveryRepository.class);
        tokens = factory.getRepository(DeviceTokenRepository.class);
        deviceTokens = new DeviceTokenService(template, tokens, clock);
        build(gateway);
    }

    private void build(PushGateway pushGateway) {
        pushService = new PushDeliveryService(
                template, deliveries, notifications, deviceTokens, pushGateway, new PushMessageFactory(),
                properties(), task -> executor.execute(task), clock);
        notificationService = new NotificationServiceImpl(notifications, new NotificationMapper(), pushService);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new NotificationEventListener(mapper, notificationService);
    }

    @AfterEach
    void dropOwnFixtureDatabase() {
        try {
            if (client != null && ownsDatabase && FIXTURE_DATABASE.matcher(database).matches()) {
                client.getDatabase(database).drop();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private PushDelivery onlyDelivery() {
        List<PushDelivery> all = deliveries.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    // ---- Token management

    @Test
    void registeringTheSameTokenTwiceKeepsOneRowAndUpdatesIt() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        clock.advance(Duration.ofMinutes(1));
        deviceTokens.register("user-1", new RegisterDeviceTokenRequest(TOKEN_A, "install-a", "ANDROID", "1.1.0"));

        assertThat(tokens.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getAppVersion()).isEqualTo("1.1.0");
            assertThat(row.isActive()).isTrue();
            assertThat(row.getUpdatedAt()).isAfter(row.getCreatedAt());
        });
    }

    @Test
    void concurrentRegistrationOfOneTokenLeavesOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Exception> errors = new CopyOnWriteArrayList<>();
        for (int index = 0; index < 8; index++) {
            pool.execute(() -> {
                try {
                    start.await();
                    deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
                } catch (Exception exception) {
                    errors.add(exception);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void refreshedTokenReplacesTheOldOneOnTheSameInstallation() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.register("user-1", registration(TOKEN_B, "install-a"));

        assertThat(deviceTokens.activeTokens("user-1")).extracting(DeviceToken::getToken).containsExactly(TOKEN_B);
        assertThat(tokens.findAll()).filteredOn(row -> !row.isActive()).singleElement().satisfies(row -> {
            assertThat(row.getDeactivationReason()).isEqualTo(DeviceTokenService.REASON_REPLACED);
            assertThat(row.getDeactivatedAt()).isNotNull();
        });
    }

    @Test
    void oneUserMayHaveManyDevices() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.register("user-1", registration(TOKEN_B, "install-b"));

        assertThat(deviceTokens.activeTokens("user-1")).hasSize(2);
    }

    @Test
    void anotherAccountOnTheSamePhoneTakesTheTokenOver() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.register("user-2", registration(TOKEN_A, "install-a"));

        assertThat(deviceTokens.activeTokens("user-1")).isEmpty();
        assertThat(deviceTokens.activeTokens("user-2")).hasSize(1);
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void anotherAccountWithANewTokenRetiresThePreviousAccountOnThatPhone() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.register("user-2", registration(TOKEN_B, "install-a"));

        assertThat(deviceTokens.activeTokens("user-1")).isEmpty();
        assertThat(deviceTokens.activeTokens("user-2")).hasSize(1);
    }

    @Test
    void logoutOnlyAffectsTheCallersOwnDevice() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));

        deviceTokens.unregister("user-2", "install-a");
        assertThat(deviceTokens.activeTokens("user-1")).hasSize(1);

        deviceTokens.unregister("user-1", "install-a");
        deviceTokens.unregister("user-1", "install-a");
        assertThat(deviceTokens.activeTokens("user-1")).isEmpty();
        assertThat(tokens.findAll().get(0).getDeactivationReason()).isEqualTo(DeviceTokenService.REASON_LOGOUT);
    }

    @Test
    void reRegisteringAfterLogoutReactivatesAndClearsTheTtlField() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.unregister("user-1", "install-a");
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));

        assertThat(tokens.findAll()).singleElement().satisfies(row -> {
            assertThat(row.isActive()).isTrue();
            assertThat(row.getDeactivatedAt()).isNull();
            assertThat(row.getDeactivationReason()).isNull();
        });
    }

    // ---- Kafka event to push

    @Test
    void kafkaEventIsStoredThenPushedToEveryActiveDevice() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.register("user-1", registration(TOKEN_B, "install-b"));

        listener.handle(event("evt-1", "user-1"));

        Notification stored = notifications.findBySourceEventId("evt-1").orElseThrow();
        assertThat(stored.getEventType()).isEqualTo("SOIL_MOISTURE_LOW");
        assertThat(stored.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(gateway.sent).hasSize(1);
        assertThat(gateway.sent.get(0).tokens()).containsExactlyInAnyOrder(TOKEN_A, TOKEN_B);
        assertThat(gateway.sent.get(0).message().data())
                .containsEntry("notificationId", stored.getId())
                .containsEntry("eventId", "evt-1")
                .containsEntry("alertId", "7")
                .doesNotContainKey("peerPhoneNumber");
        PushDelivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(delivery.getAcceptedCount()).isEqualTo(2);
        assertThat(delivery.getSentAt()).isNotNull();
    }

    @Test
    void anotherUsersDevicesReceiveNothing() {
        deviceTokens.register("user-2", registration(TOKEN_A, "install-a"));

        listener.handle(event("evt-1", "user-1"));

        assertThat(gateway.sent).isEmpty();
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.NO_DEVICE);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void redeliveredKafkaEventNeitherDuplicatesTheInboxNorThePush() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));

        listener.handle(event("evt-1", "user-1"));
        listener.handle(event("evt-1", "user-1"));
        listener.handle(event("evt-1", "user-1"));

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(deliveries.count()).isEqualTo(1);
        assertThat(gateway.sent).hasSize(1);
    }

    @Test
    void concurrentRedeliveryStillSendsOnce() throws Exception {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        ExecutorService pool = Executors.newFixedThreadPool(6);
        executor = pool;
        for (int index = 0; index < 6; index++) {
            pool.execute(() -> listener.handle(event("evt-1", "user-1")));
        }
        pool.awaitTermination(3, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(gateway.sent).hasSize(1);
    }

    @Test
    void replayedOldEventIsNotPushed() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        Notification old = new Notification();
        old.setReceiverId("user-1");
        old.setTitle("Cũ");
        old.setMessage("Đã xảy ra từ lâu");
        old.setSourceEventId("evt-old");
        old.setCreatedAt(clock.instant().minus(Duration.ofDays(2)));
        notifications.insert(old);

        listener.handle(event("evt-old", "user-1"));

        assertThat(gateway.sent).isEmpty();
        assertThat(deliveries.count()).isZero();
    }

    @Test
    void disabledGatewayStoresTheNotificationAndTracksNoDelivery() {
        build(new DisabledPushGateway());
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));

        listener.handle(event("evt-1", "user-1"));

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(deliveries.count()).isZero();
    }

    // ---- Failures

    @Test
    void gatewayExceptionNeverReachesTheConsumerAndTheInboxKeepsTheNotification() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        gateway.behaviour = token -> {
            throw new IllegalStateException("provider exploded");
        };

        listener.handle(event("evt-1", "user-1"));

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.SENDING);

        // The stale claim is picked up by the scan once the provider is back.
        gateway.behaviour = token -> PushSendResult.accepted();
        clock.advance(Duration.ofMinutes(6));
        pushService.processDue();
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.SENT);
    }

    @Test
    void expiredTokenIsDeactivatedAndNotRetried() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        gateway.behaviour = token -> PushSendResult.failed(PushSendResult.Outcome.INVALID_TOKEN, "UNREGISTERED");

        listener.handle(event("evt-1", "user-1"));

        assertThat(deviceTokens.activeTokens("user-1")).isEmpty();
        assertThat(tokens.findAll().get(0).getDeactivationReason()).isEqualTo(DeviceTokenService.REASON_INVALID);
        PushDelivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(delivery.getInvalidTokenCount()).isEqualTo(1);
        assertThat(delivery.getLastError()).isEqualTo("UNREGISTERED");
    }

    @Test
    void transientFailureRetriesOnlyTheFailedTokenAfterBackoff() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        deviceTokens.register("user-1", registration(TOKEN_B, "install-b"));
        gateway.behaviour = token -> token.equals(TOKEN_A)
                ? PushSendResult.accepted()
                : PushSendResult.failed(PushSendResult.Outcome.RETRYABLE, "UNAVAILABLE");

        listener.handle(event("evt-1", "user-1"));

        PushDelivery waiting = onlyDelivery();
        assertThat(waiting.getStatus()).isEqualTo(PushDeliveryStatus.RETRY);
        assertThat(waiting.getAcceptedCount()).isEqualTo(1);
        assertThat(waiting.getNextAttemptAt()).isEqualTo(clock.instant().plusSeconds(30));

        // Not due yet: a scan and a Kafka redelivery must both leave it alone.
        pushService.processDue();
        listener.handle(event("evt-1", "user-1"));
        assertThat(gateway.sent).hasSize(1);

        gateway.behaviour = token -> PushSendResult.accepted();
        clock.advance(Duration.ofSeconds(31));
        pushService.processDue();

        assertThat(gateway.sent).hasSize(2);
        assertThat(gateway.sent.get(1).tokens()).containsExactly(TOKEN_B);
        PushDelivery done = onlyDelivery();
        assertThat(done.getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(done.getAcceptedCount()).isEqualTo(2);
        assertThat(done.getAttempts()).isEqualTo(2);
    }

    @Test
    void retriesStopAtMaxAttempts() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        gateway.behaviour = token -> PushSendResult.failed(PushSendResult.Outcome.RETRYABLE, "QUOTA_EXCEEDED");

        listener.handle(event("evt-1", "user-1"));
        for (int round = 0; round < 6; round++) {
            clock.advance(Duration.ofMinutes(20));
            pushService.processDue();
        }

        assertThat(gateway.sent).hasSize(3);
        PushDelivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(3);
        assertThat(delivery.getLastError()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void providerRetryAfterHintIsHonoured() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        gateway.behaviour = token -> new PushSendResult(
                PushSendResult.Outcome.RETRYABLE, "QUOTA_EXCEEDED", Duration.ofMinutes(10));

        listener.handle(event("evt-1", "user-1"));

        assertThat(onlyDelivery().getNextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
    }

    @Test
    void permanentFailureKeepsTheTokenAndStops() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        gateway.behaviour = token -> PushSendResult.failed(PushSendResult.Outcome.PERMANENT, "INVALID_ARGUMENT");

        listener.handle(event("evt-1", "user-1"));

        assertThat(deviceTokens.activeTokens("user-1")).hasSize(1);
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
    }

    @Test
    void fullExecutorLeavesTheDeliveryForTheScan() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        executor = task -> {
            throw new TaskRejectedException("full");
        };

        listener.handle(event("evt-1", "user-1"));

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);

        executor = inline;
        pushService.processDue();
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.SENT);
    }

    @Test
    void anUnexpectedQueueFailureStillDoesNotFailTheConsumer() {
        executor = task -> {
            throw new RejectedExecutionException("shutting down");
        };

        listener.handle(event("evt-1", "user-1"));

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void deletedNotificationIsNotRetried() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        gateway.behaviour = token -> PushSendResult.failed(PushSendResult.Outcome.RETRYABLE, "UNAVAILABLE");
        listener.handle(event("evt-1", "user-1"));

        notificationService.delete("user-1", notifications.findAll().get(0).getId());
        clock.advance(Duration.ofMinutes(1));
        pushService.processDue();

        assertThat(gateway.sent).hasSize(1);
        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.CANCELLED);
    }

    @Test
    void readingANotificationDoesNotDisturbPushState() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));
        listener.handle(event("evt-1", "user-1"));

        notificationService.markAsRead("user-1", notifications.findAll().get(0).getId());

        assertThat(onlyDelivery().getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(notifications.findAll().get(0).isRead()).isTrue();
    }

    @Test
    void apiCreatedNotificationWithoutEventIdIsAlsoPushed() {
        deviceTokens.register("user-1", registration(TOKEN_A, "install-a"));

        notificationService.createNotification(new CreateNotificationRequest(
                "user-1", "Tiêu đề", "Nội dung", NotificationType.SYSTEM, Map.of(), null));

        assertThat(gateway.sent).hasSize(1);
        assertThat(gateway.sent.get(0).message().data()).containsKey("eventId");
    }

    private record Sent(PushMessage message, List<String> tokens) {
    }

    private static final class FakeGateway implements PushGateway {
        private final List<Sent> sent = new CopyOnWriteArrayList<>();
        private volatile Function<String, PushSendResult> behaviour = token -> PushSendResult.accepted();

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<PushSendResult> send(PushMessage message, List<String> tokens) {
            sent.add(new Sent(message, tokens));
            return tokens.stream().map(behaviour).toList();
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
