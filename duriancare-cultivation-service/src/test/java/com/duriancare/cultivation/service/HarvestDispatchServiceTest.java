package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.security.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class HarvestDispatchServiceTest {
    final HarvestBatchRepository batches = mock(HarvestBatchRepository.class);
    final ExportReleaseRepository releases = mock(ExportReleaseRepository.class);
    final CultivationSeasonRepository seasons = mock(CultivationSeasonRepository.class);
    final HarvestDispatchRepository dispatches = mock(HarvestDispatchRepository.class);
    final CultivationAccessGuard guard = mock(CultivationAccessGuard.class);
    final CultivationActor actor = new CultivationActor("farmer", "", "FARMER");
    final HarvestDispatchService service = new HarvestDispatchService(batches, releases, seasons, dispatches, guard);
    final Instant harvest = Instant.parse("2025-10-15T07:00:00Z");
    HarvestBatch batch;
    HarvestDispatch.Evidence evidence;

    @BeforeEach void setup() {
        batch = new HarvestBatch("b", "B", "s", "f", "z", harvest, n("1850"), "KG", "CN",
                LocalDate.parse("2025-10-10"), RiskLevel.LOW, HarvestBatchStatus.HARVESTED, "farmer", harvest);
        evidence = new HarvestDispatch.Evidence("harvest-doc", "grade-doc", "pack-doc", "dispatch-doc",
                n("1850"), n("1700"), n("150"), n("1600"), n("1500"), "KG",
                harvest.plusSeconds(3600), harvest.plusSeconds(7200), "receiver-from-document");
        when(batches.findById("b")).thenReturn(Optional.of(batch));
        when(seasons.findById("s")).thenReturn(Optional.of(new CultivationSeason("s", "f", "z", "season", "DURIAN", null,
                LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), "farmer", harvest, harvest)));
        when(releases.findById("r")).thenReturn(Optional.of(new ExportRelease("r", "R", "b", "CN", Map.of(), Map.of(),
                ExportReleaseStatus.RELEASED, "farmer", harvest, "reviewer", harvest, null, harvest, harvest)));
        when(dispatches.insert(any(HarvestDispatch.class))).thenAnswer(i -> i.getArgument(0));
        when(dispatches.save(any(HarvestDispatch.class))).thenAnswer(i -> i.getArgument(0));
        when(guard.canView(actor, "f", "z")).thenReturn(true);
    }

    @Test void recordsEvidenceWithoutAutomaticallyConfirmingLot() {
        HarvestDispatch saved = service.record(actor, "b", "r", evidence);
        assertThat(saved.confirmedAt()).isNull();
        assertThat(saved.recordedBy()).isEqualTo("farmer");
        assertThat(saved.farmId()).isEqualTo("f");
        verify(batches, never()).save(any());
    }

    @Test void noDossierAndUnverifiedLotsHaveUnknownQuantities() {
        when(dispatches.findByFarmId("f")).thenReturn(List.of());
        assertThat(service.summary(actor, "f", null).status()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(service.summary(actor, "f", null).quantitiesByUnit()).isNull();
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(false)));
        assertThat(service.summary(actor, "f", null).shippedByMonth()).isNull();
    }

    @Test void aggregatesOnlyConfirmedValidEvidenceWithMeasuredRate() {
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(true)));
        var summary = service.summary(actor, "f", "s");
        assertThat(summary.confirmedLotCount()).isEqualTo(1);
        assertThat(summary.quantitiesByUnit().get("KG").shipped()).isEqualByComparingTo("1500");
        assertThat(summary.quantitiesByUnit().get("KG").qualityPassRate()).isEqualByComparingTo("91.8919");
        assertThat(summary.shippedByMonth().get("2025-10").get("KG")).isEqualByComparingTo("1500");
    }

    @Test void rejectsMissingEvidenceWithoutWriting() {
        assertThatThrownBy(() -> service.record(actor, "b", "r", null)).isInstanceOf(IllegalArgumentException.class);
        verify(dispatches, never()).insert(any(HarvestDispatch.class));
    }

    @Test void rejectsWrongSeasonScopeWithoutWriting() {
        when(seasons.findById("s")).thenReturn(Optional.of(new CultivationSeason("s", "another-farm", "z", "season", null, null,
                LocalDate.parse("2025-01-01"), null, "farmer", harvest, harvest)));
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).hasMessageContaining("scope");
        verify(dispatches, never()).insert(any(HarvestDispatch.class));
    }

    @Test void excludesOrphanOrChangedLotFromSummary() {
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(true)));
        when(batches.findById("b")).thenReturn(Optional.empty());
        assertThat(service.summary(actor, "f", null).excludedDossierIds()).containsExactly("b");
        assertThat(service.summary(actor, "f", null).quantitiesByUnit()).isNull();
    }

    @Test void requiresExplicitDocumentCheckBeforeConfirmation() {
        assertThatThrownBy(() -> service.confirm(actor, "b", false)).hasMessageContaining("Explicit");
        verify(dispatches, never()).save(any(HarvestDispatch.class));
    }

    @Test void confirmedRetryDoesNotWriteAgain() {
        when(dispatches.findById("b")).thenReturn(Optional.of(record(true)));
        assertThat(service.confirm(actor, "b", true).confirmedBy()).isEqualTo("farmer");
        verify(dispatches, never()).save(any(HarvestDispatch.class));
    }

    @Test void insertRaceReturnsExistingIdenticalRecord() {
        when(dispatches.findById("b")).thenReturn(Optional.empty(), Optional.of(record(false)));
        when(dispatches.insert(any(HarvestDispatch.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThat(service.record(actor, "b", "r", evidence).id()).isEqualTo("b");
        verify(dispatches, times(1)).insert(any(HarvestDispatch.class));
    }

    @Test void refusesCrossOwnerWritesAndOmitsInvisibleRecords() {
        doThrow(new CultivationAccessDeniedException("denied")).when(guard).requireCreate(actor, "HarvestDispatch", "b", "f", "z");
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).isInstanceOf(CultivationAccessDeniedException.class);
        verify(dispatches, never()).insert(any(HarvestDispatch.class));
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(true)));
        when(guard.canView(actor, "f", "z")).thenReturn(false);
        assertThat(service.summary(actor, "f", null).quantitiesByUnit()).isNull();
    }

    @Test void rejectsExcessShipmentAndMissingDocumentReference() {
        var excess = new HarvestDispatch.Evidence("h", "g", "p", "d", n("1850"), n("1700"), n("150"), n("1600"),
                n("1601"), "KG", harvest, harvest.plusSeconds(10), "receiver");
        assertThatThrownBy(() -> service.record(actor, "b", "r", excess)).hasMessageContaining("exceeds");
        var missing = new HarvestDispatch.Evidence("", "g", "p", "d", n("1850"), n("1700"), n("150"), n("1600"),
                n("1500"), "KG", harvest, harvest.plusSeconds(10), "receiver");
        assertThatThrownBy(() -> service.record(actor, "b", "r", missing)).hasMessageContaining("references");
    }

    private HarvestDispatch record(boolean confirmed) {
        return new HarvestDispatch("b", "r", "f", "z", "s", evidence, "farmer", harvest.plusSeconds(8000),
                confirmed ? "farmer" : null, confirmed ? harvest.plusSeconds(9000) : null, 0L);
    }

    @Test void confirmationRecordsAuthenticatedActorWithoutEditingLot() {
        when(dispatches.findById("b")).thenReturn(Optional.of(record(false)));
        var result = service.confirm(actor, "b", true);
        assertThat(result.confirmedBy()).isEqualTo(actor.userId());
        assertThat(result.confirmedAt()).isNotNull();
        verify(batches, never()).save(any());
        verify(dispatches).save(any(HarvestDispatch.class));
    }

    @Test void missingOrUnreleasedExportDossierCannotBeRecorded() {
        when(releases.findById("r")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).hasMessageContaining("not found");
        when(releases.findById("r")).thenReturn(Optional.of(new ExportRelease("r", "R", "b", "CN", Map.of(), Map.of(),
                ExportReleaseStatus.APPROVED, "farmer", harvest, null, null, null, null, harvest)));
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).hasMessageContaining("released dossier");
        verify(dispatches, never()).insert(any(HarvestDispatch.class));
    }

    @Test void mismatchedReleaseAndPrematurePackingAreRejected() {
        var premature = new HarvestDispatch.Evidence("h", "g", "p", "d", n("1850"), n("1700"), n("150"), n("1600"),
                n("1500"), "KG", harvest.minusSeconds(1), harvest.plusSeconds(10), "receiver");
        assertThatThrownBy(() -> service.record(actor, "b", "r", premature)).hasMessageContaining("chronology");
        when(releases.findById("r")).thenReturn(Optional.of(new ExportRelease("r", "R", "another-lot", "CN", Map.of(), Map.of(),
                ExportReleaseStatus.RELEASED, "farmer", harvest, null, null, null, harvest, harvest)));
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).hasMessageContaining("released dossier");
    }

    @Test void differentDuplicateDossierCannotOverwriteExistingEvidence() {
        when(dispatches.findById("b")).thenReturn(Optional.of(new HarvestDispatch("b", "old-release", "f", "z", "s",
                evidence, "farmer", harvest, null, null, 0L)));
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).hasMessageContaining("already exists");
        verify(dispatches, never()).insert(any(HarvestDispatch.class));
        verify(dispatches, never()).save(any(HarvestDispatch.class));
    }

    @Test void unrecognizedSeedStateMappedToDraftCannotEnterReport() {
        HarvestBatch draft = new HarvestBatch("b", "B", "s", "f", "z", harvest, n("1850"), "KG", "CN", null,
                RiskLevel.LOW, HarvestBatchStatus.DRAFT, "farmer", harvest);
        when(batches.findById("b")).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.record(actor, "b", "r", evidence)).hasMessageContaining("Only harvested");
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(true)));
        assertThat(service.summary(actor, "f", null).quantitiesByUnit()).isNull();
    }

    @Test void sourceReferenceDoesNotPromotePendingDossierAndSeasonFiltersApply() {
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(false)));
        assertThat(service.summary(actor, "f", null).confirmedLotCount()).isZero();
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(true)));
        assertThat(service.summary(actor, "f", "different-season").quantitiesByUnit()).isNull();
    }

    @Test void reportSumsDistinctConfirmedLotsAndIgnoresPendingLot() {
        HarvestBatch second = new HarvestBatch("b2", "B2", "s", "f", "z", harvest, n("1850"), "KG", "CN", null,
                RiskLevel.LOW, HarvestBatchStatus.HARVESTED, "farmer", harvest);
        when(batches.findById("b2")).thenReturn(Optional.of(second));
        when(releases.findById("r2")).thenReturn(Optional.of(new ExportRelease("r2", "R2", "b2", "CN", Map.of(), Map.of(),
                ExportReleaseStatus.RELEASED, "farmer", harvest, null, null, null, harvest, harvest)));
        HarvestDispatch secondRecord = new HarvestDispatch("b2", "r2", "f", "z", "s", evidence, "farmer",
                harvest.plusSeconds(8000), "farmer", harvest.plusSeconds(9000), 0L);
        when(dispatches.findByFarmId("f")).thenReturn(List.of(record(true), secondRecord, record(false)));
        var result = service.summary(actor, "f", "s");
        assertThat(result.confirmedLotCount()).isEqualTo(2);
        assertThat(result.quantitiesByUnit().get("KG").harvested()).isEqualByComparingTo("3700");
        assertThat(result.quantitiesByUnit().get("KG").qualified()).isEqualByComparingTo("3400");
        assertThat(result.quantitiesByUnit().get("KG").shipped()).isEqualByComparingTo("3000");
        assertThat(result.shippedByMonth().get("2025-10").get("KG")).isEqualByComparingTo("3000");
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
}
