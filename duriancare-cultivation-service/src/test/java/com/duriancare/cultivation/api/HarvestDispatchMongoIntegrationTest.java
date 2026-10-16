package com.duriancare.cultivation.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.security.*;
import com.duriancare.cultivation.service.HarvestDispatchService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

/** Real Mongo persistence, isolated UUID database; external farm authorization is a fixture stub.
 * This is deliberately NOT an authenticated Gateway E2E or evidence of a real shipment.
 */
@EnabledIfEnvironmentVariable(named = "DURIANCARE_FIXTURE_MONGO_URI", matches = ".+")
class HarvestDispatchMongoIntegrationTest {
    private final String database = "dispatch_acceptance_fixture_" + UUID.randomUUID().toString().replace("-", "");
    private final CultivationActor owner = new CultivationActor("fixture-owner", "", "FARMER");
    private MongoClient client;
    private MongoTemplate template;
    private HarvestDispatchService service;
    private HarvestDispatchRepository dispatches;
    private HarvestBatch lot;
    private HarvestDispatch.Evidence evidence;

    @BeforeEach void setup() {
        client = MongoClients.create(System.getenv("DURIANCARE_FIXTURE_MONGO_URI"));
        template = new MongoTemplate(client, database);
        assertThat(template.getCollectionNames()).isEmpty();
        var factory = new MongoRepositoryFactory(template);
        var batches = factory.getRepository(HarvestBatchRepository.class);
        var releases = factory.getRepository(ExportReleaseRepository.class);
        var seasons = factory.getRepository(CultivationSeasonRepository.class);
        dispatches = factory.getRepository(HarvestDispatchRepository.class);
        Instant harvest = Instant.parse("2025-10-01T00:00:00Z");
        lot = new HarvestBatch("fixture-lot", "FIXTURE_ONLY", "fixture-season", "fixture-farm", "fixture-zone",
                harvest, new BigDecimal("100"), "KG", "VN", null, RiskLevel.LOW, HarvestBatchStatus.HARVESTED,
                owner.userId(), harvest);
        batches.insert(lot);
        seasons.insert(new CultivationSeason("fixture-season", "fixture-farm", "fixture-zone", "FIXTURE_ONLY",
                null, null, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), owner.userId(), harvest, harvest));
        releases.insert(new ExportRelease("fixture-release", "FIXTURE_ONLY", lot.id(), "VN", Map.of(), Map.of(),
                ExportReleaseStatus.RELEASED, owner.userId(), harvest, null, null, null, harvest, harvest));
        FarmAccessClient access = mock(FarmAccessClient.class);
        when(access.canAccess(any(), any(), anyString(), nullable(String.class))).thenAnswer(i -> {
            CultivationActor actor = i.getArgument(0);
            return owner.equals(actor) && "fixture-farm".equals(i.getArgument(2)) &&
                    (i.getArgument(3) == null || "fixture-zone".equals(i.getArgument(3)));
        });
        service = new HarvestDispatchService(batches, releases, seasons, dispatches, new CultivationAccessGuard(access));
        evidence = evidence("70", "2025-10-03T00:00:00Z");
    }

    @AfterEach void cleanupOnlyThisTestsDocuments() {
        try {
            if (template != null) {
                assertThat(database).startsWith("dispatch_acceptance_fixture_");
                for (var entry : Map.of("harvest_batches", "fixture-lot", "harvest_dispatches", "fixture-lot",
                        "export_releases", "fixture-release", "cultivation_seasons", "fixture-season").entrySet()) {
                    template.getCollection(entry.getKey()).deleteOne(new org.bson.Document("_id", entry.getValue()));
                    assertThat(template.getCollection(entry.getKey()).countDocuments()).isZero();
                }
            }
        } finally { if (client != null) client.close(); }
    }

    @Test void realPersistencePendingConfirmationAggregationAndRetry() {
        var created = service.record(owner, lot.id(), "fixture-release", evidence);
        assertThat(created.version()).isNotNull();
        assertThat(service.get(owner, lot.id()).farmId()).isEqualTo("fixture-farm");
        assertThat(service.summary(owner, "fixture-farm", null).quantitiesByUnit()).isNull();
        var confirmed = service.confirm(owner, lot.id(), true);
        assertThat(confirmed.confirmedBy()).isEqualTo(owner.userId());
        assertThat(service.summary(owner, "fixture-farm", null).quantitiesByUnit().get("KG").shipped())
                .isEqualByComparingTo("70");
        assertThat(service.record(owner, lot.id(), "fixture-release", evidence).id()).isEqualTo(lot.id());
        assertThat(service.confirm(owner, lot.id(), true).version()).isEqualTo(confirmed.version());
        assertThat(dispatches.count()).isEqualTo(1);
    }

    @Test void invalidWeightsDatesAndMissingConfirmationCannotContribute() {
        assertThatThrownBy(() -> service.record(owner, lot.id(), "fixture-release", evidence("81", "2025-10-03T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(owner, lot.id(), "fixture-release", evidence("70", "2025-09-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        service.record(owner, lot.id(), "fixture-release", evidence);
        assertThatThrownBy(() -> service.confirm(owner, lot.id(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.summary(owner, "fixture-farm", null).status()).isEqualTo("NO_VERIFIED_DATA");
    }

    @Test void concurrentIdenticalSubmissionsPersistExactlyOneDossier() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<HarvestDispatch> submit = () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return service.record(owner, lot.id(), "fixture-release", evidence);
            };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).id()).isEqualTo(lot.id());
            assertThat(second.get(15, TimeUnit.SECONDS).id()).isEqualTo(lot.id());
            assertThat(dispatches.count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void otherActorWrongReleaseAndCorruptedScopeAreRejected() {
        var stranger = new CultivationActor("fixture-other-farm", "", "FARMER");
        assertThatThrownBy(() -> service.record(stranger, lot.id(), "fixture-release", evidence))
                .isInstanceOf(CultivationAccessDeniedException.class);
        assertThatThrownBy(() -> service.record(owner, lot.id(), "wrong-release", evidence))
                .isInstanceOf(IllegalArgumentException.class);
        var r = service.record(owner, lot.id(), "fixture-release", evidence);
        dispatches.save(new HarvestDispatch(r.id(), r.exportReleaseId(), "wrong-farm", "wrong-zone", "wrong-season",
                r.evidence(), r.recordedBy(), r.recordedAt(), null, null, r.version()));
        assertThatThrownBy(() -> service.get(owner, lot.id())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(owner, lot.id(), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.summary(stranger, "fixture-farm", null))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    private HarvestDispatch.Evidence evidence(String shipped, String date) {
        return new HarvestDispatch.Evidence("TEST_NOT_DOCUMENT-h", "TEST_NOT_DOCUMENT-g", "TEST_NOT_DOCUMENT-p",
                "TEST_NOT_DOCUMENT-d", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("10"),
                new BigDecimal("80"), new BigDecimal(shipped), "KG", Instant.parse("2025-10-02T00:00:00Z"),
                Instant.parse(date), "TEST_ONLY_NOT_REAL_CUSTOMER");
    }
}
