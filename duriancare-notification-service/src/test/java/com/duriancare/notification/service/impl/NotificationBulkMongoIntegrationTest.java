package com.duriancare.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.notification.domain.Notification;
import com.duriancare.notification.domain.NotificationStatus;
import com.duriancare.notification.mapper.NotificationMapper;
import com.duriancare.notification.repository.NotificationRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

@EnabledIfEnvironmentVariable(named = "DURIANCARE_FIXTURE_MONGO_URI", matches = ".+")
class NotificationBulkMongoIntegrationTest {
    private static final Pattern FIXTURE_DATABASE = Pattern.compile("notification_fixture_[0-9a-f]{32}");

    // One fresh database per test method (JUnit creates a new instance per method), so parallel runs never share one.
    private final String database = "notification_fixture_" + UUID.randomUUID().toString().replace("-", "");
    private MongoClient client;
    // Set only once we have confirmed the database did not exist before: cleanup never drops a database it did not create.
    private boolean ownsDatabase;
    private NotificationRepository repository;
    private NotificationServiceImpl service;

    @BeforeEach void setup() {
        assertThat(database).matches(FIXTURE_DATABASE);
        client = MongoClients.create(System.getenv("DURIANCARE_FIXTURE_MONGO_URI"));
        assertThat(client.listDatabaseNames().into(new ArrayList<>())).doesNotContain(database);
        ownsDatabase = true;
        MongoTemplate template = new MongoTemplate(client, database);
        repository = new MongoRepositoryFactory(template).getRepository(NotificationRepository.class);
        service = new NotificationServiceImpl(
                repository,
                new NotificationMapper(),
                org.mockito.Mockito.mock(com.duriancare.notification.service.push.PushDeliveryService.class));
        for (String id : List.of("own-1", "own-2", "other", "already-read")) {
            Notification notification = new Notification();
            notification.setId(id);
            notification.setReceiverId(id.equals("other") ? "other-owner" : "owner");
            notification.setRead(id.equals("already-read"));
            notification.setStatus(notification.isRead() ? NotificationStatus.READ : NotificationStatus.UNREAD);
            repository.insert(notification);
        }
    }

    // @AfterEach runs even when the test or @BeforeEach failed; nothing here can throw before the drop.
    @AfterEach void dropOwnFixtureDatabase() {
        try {
            if (client != null && ownsDatabase && FIXTURE_DATABASE.matcher(database).matches()) {
                client.getDatabase(database).drop();
            }
        } finally {
            if (client != null) client.close();
        }
    }

    @Test void scopeRetryAndEmptyInbox() {
        assertThat(service.markAllAsRead(" owner ").updatedCount()).isEqualTo(2);
        assertThat(service.markAllAsRead("owner").updatedCount()).isZero();
        assertThat(service.markAllAsRead("empty-owner").updatedCount()).isZero();
        assertThat(repository.findById("other").orElseThrow().isRead()).isFalse();
        assertThat(repository.findById("own-1").orElseThrow().getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test void concurrentRequestsCountEachModifiedDocumentOnlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return service.markAllAsRead("owner").updatedCount(); });
            var second = executor.submit(() -> { start.await(); return service.markAllAsRead("owner").updatedCount(); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(repository.countByReceiverIdAndIsReadFalse("owner")).isZero();
            assertThat(repository.findById("other").orElseThrow().isRead()).isFalse();
            assertThat(repository.count()).isEqualTo(4);
        } finally { executor.shutdownNow(); }
    }
}
