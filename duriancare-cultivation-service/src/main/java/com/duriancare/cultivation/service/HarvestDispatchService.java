package com.duriancare.cultivation.service;

import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import com.duriancare.cultivation.security.CultivationActor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HarvestDispatchService {
    private final HarvestBatchRepository batches;
    private final ExportReleaseRepository releases;
    private final CultivationSeasonRepository seasons;
    private final HarvestDispatchRepository dispatches;
    private final CultivationAccessGuard guard;

    public HarvestDispatchService(HarvestBatchRepository batches, ExportReleaseRepository releases,
            CultivationSeasonRepository seasons, HarvestDispatchRepository dispatches, CultivationAccessGuard guard) {
        this.batches = batches;
        this.releases = releases;
        this.seasons = seasons;
        this.dispatches = dispatches;
        this.guard = guard;
    }

    public HarvestDispatch get(CultivationActor actor, String batchId) {
        HarvestBatch batch = batch(batchId);
        guard.requireView(actor, "HarvestDispatch", batchId, batch.farmId(), batch.plotId());
        HarvestDispatch record = dispatches.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Dispatch dossier not found"));
        requireMatchingScope(batch, record);
        return record;
    }

    public HarvestDispatch record(CultivationActor actor, String batchId, String releaseId, HarvestDispatch.Evidence evidence) {
        HarvestBatch batch = batch(batchId);
        guard.requireCreate(actor, "HarvestDispatch", batchId, batch.farmId(), batch.plotId());
        validate(batch, releaseId, evidence);
        HarvestDispatch existing = dispatches.findById(batchId).orElse(null);
        if (existing != null) {
            requireMatchingScope(batch, existing);
            return sameOrConflict(existing, releaseId, evidence);
        }
        HarvestDispatch draft = new HarvestDispatch(batchId, releaseId, batch.farmId(), batch.plotId(),
                batch.cultivationSeasonId(), evidence, actor.userId(), Instant.now(), null, null, null);
        try {
            return dispatches.insert(draft);
        } catch (DuplicateKeyException race) {
            HarvestDispatch raced = dispatches.findById(batchId).orElseThrow(() -> race);
            requireMatchingScope(batch, raced);
            return sameOrConflict(raced, releaseId, evidence);
        }
    }

    public HarvestDispatch confirm(CultivationActor actor, String batchId, boolean sourceDocumentsChecked) {
        HarvestBatch batch = batch(batchId);
        guard.requireUpdate(actor, "HarvestDispatch", batchId, batch.farmId(), batch.plotId());
        require(sourceDocumentsChecked, "Explicit source-document confirmation is required");
        HarvestDispatch record = dispatches.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Dispatch dossier not found"));
        validateRecord(batch, record);
        if (record.confirmedAt() != null) return record;
        return dispatches.save(new HarvestDispatch(record.id(), record.exportReleaseId(), record.farmId(), record.plotId(),
                record.cultivationSeasonId(), record.evidence(), record.recordedBy(), record.recordedAt(),
                actor.userId(), Instant.now(), record.version()));
    }

    public Summary summary(CultivationActor actor, String farmId, String seasonId) {
        require(StringUtils.hasText(farmId), "Farm id is required");
        guard.requireView(actor, "HarvestDispatchReport", null, farmId, null);
        Map<String, Quantities> byUnit = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> shippedByMonth = new TreeMap<>();
        List<String> excluded = new ArrayList<>();
        int confirmed = 0;
        for (HarvestDispatch record : dispatches.findByFarmId(farmId)) {
            if (seasonId != null && !seasonId.equals(record.cultivationSeasonId())) continue;
            if (!guard.canView(actor, record.farmId(), record.plotId())) continue;
            if (record.confirmedAt() == null || !StringUtils.hasText(record.confirmedBy())) continue;
            HarvestBatch batch = batches.findById(record.id()).orElse(null);
            try {
                require(batch != null, "Harvest lot not found");
                ExportRelease sourceRelease = releases.findById(record.exportReleaseId()).orElse(null);
                require(!"DEMO_SYNTHETIC".equals(batch.dataOrigin()) && sourceRelease != null &&
                        !"DEMO_SYNTHETIC".equals(sourceRelease.dataOrigin()), "Demo fixtures are not verified shipment evidence");
                validateRecord(batch, record);
                require(!record.confirmedAt().isAfter(Instant.now()), "Invalid confirmation date");
                require(record.recordedAt() != null && StringUtils.hasText(record.recordedBy()) &&
                        !record.confirmedAt().isBefore(record.recordedAt()) &&
                        !record.recordedAt().isBefore(record.evidence().shippedAt()), "Invalid source confirmation chronology");
            } catch (IllegalArgumentException | NoSuchElementException invalid) {
                excluded.add(record.id());
                continue;
            }
            var e = record.evidence();
            byUnit.merge(e.quantityUnit(), new Quantities(e.harvestedQuantity(), e.qualifiedQuantity(),
                    e.rejectedQuantity(), e.packedQuantity(), e.shippedQuantity(), null), Quantities::add);
            String month = e.shippedAt().atOffset(ZoneOffset.UTC).toLocalDate().toString().substring(0, 7);
            shippedByMonth.computeIfAbsent(month, key -> new TreeMap<>()).merge(e.quantityUnit(), e.shippedQuantity(), BigDecimal::add);
            confirmed++;
        }
        byUnit.replaceAll((unit, q) -> q.withRate());
        return new Summary(confirmed == 0 ? "NO_VERIFIED_DATA" : "OPERATOR_CONFIRMED_RECORDS", confirmed,
                confirmed == 0 ? null : byUnit, confirmed == 0 ? null : shippedByMonth, List.copyOf(excluded));
    }

    public HarvestDispatch delivered(CultivationActor actor, String batchId, String sourceRef, Instant deliveredAt) {
        HarvestBatch batch = batch(batchId);
        guard.requireUpdate(actor, "HarvestDispatch", batchId, batch.farmId(), batch.plotId());
        HarvestDispatch record = get(actor, batchId);
        validateRecord(batch, record);
        require(record.confirmedAt() != null, "Confirm dispatch before delivery");
        require(StringUtils.hasText(sourceRef) && deliveredAt != null && !deliveredAt.isAfter(Instant.now()) &&
                !deliveredAt.isBefore(record.evidence().shippedAt()), "Valid delivery proof and chronology are required");
        if (record.delivery() != null) {
            require(record.delivery().sourceRef().equals(sourceRef.trim()) && record.delivery().deliveredAt().equals(deliveredAt),
                    "A different delivery confirmation already exists");
            return record;
        }
        return dispatches.save(new HarvestDispatch(record.id(), record.exportReleaseId(), record.farmId(), record.plotId(),
                record.cultivationSeasonId(), record.evidence(), record.recordedBy(), record.recordedAt(),
                record.confirmedBy(), record.confirmedAt(), record.version(),
                new HarvestDispatch.Delivery(sourceRef.trim(), deliveredAt, actor.userId(), Instant.now())));
    }

    private HarvestBatch batch(String id) {
        return batches.findById(id).orElseThrow(() -> new NoSuchElementException("Harvest lot not found"));
    }

    private HarvestDispatch sameOrConflict(HarvestDispatch existing, String releaseId, HarvestDispatch.Evidence evidence) {
        require(Objects.equals(existing.exportReleaseId(), releaseId) && Objects.equals(existing.evidence(), evidence),
                "A different dispatch dossier already exists for this lot");
        return existing;
    }

    private void validateRecord(HarvestBatch batch, HarvestDispatch record) {
        requireMatchingScope(batch, record);
        validate(batch, record.exportReleaseId(), record.evidence());
    }

    private void requireMatchingScope(HarvestBatch batch, HarvestDispatch record) {
        require(Objects.equals(batch.farmId(), record.farmId()) && Objects.equals(batch.plotId(), record.plotId()) &&
                Objects.equals(batch.id(), record.id()) &&
                Objects.equals(batch.cultivationSeasonId(), record.cultivationSeasonId()), "Dispatch scope mismatch");
    }

    private void validate(HarvestBatch batch, String releaseId, HarvestDispatch.Evidence e) {
        require(batch.status() == HarvestBatchStatus.HARVESTED, "Only harvested lots can have actual dispatch records");
        CultivationSeason season = seasons.findById(batch.cultivationSeasonId())
                .orElseThrow(() -> new IllegalArgumentException("Harvest season not found"));
        require(Objects.equals(batch.farmId(), season.farmId()) && Objects.equals(batch.plotId(), season.plotId()), "Season scope mismatch");
        require(StringUtils.hasText(releaseId), "Export release is required");
        ExportRelease release = releases.findById(releaseId).orElseThrow(() -> new IllegalArgumentException("Export release not found"));
        require(Objects.equals(batch.id(), release.harvestBatchId()) && release.status() == ExportReleaseStatus.RELEASED &&
                release.releasedAt() != null, "A released dossier for this lot is required");
        require(e != null, "Source evidence is required");
        require(StringUtils.hasText(e.harvestSourceRef()) && StringUtils.hasText(e.gradingSourceRef()) &&
                StringUtils.hasText(e.packingSourceRef()) && StringUtils.hasText(e.dispatchSourceRef()), "All source document references are required");
        require(StringUtils.hasText(e.destination()) && StringUtils.hasText(e.quantityUnit()) &&
                Objects.equals(e.quantityUnit(), batch.quantityUnit()), "Destination and matching quantity unit are required");
        require(batch.quantity() != null && batch.quantity().signum() > 0, "Invalid recorded harvest quantity");
        for (BigDecimal quantity : Arrays.asList(e.harvestedQuantity(), e.qualifiedQuantity(), e.rejectedQuantity(), e.packedQuantity(), e.shippedQuantity())) {
            require(quantity != null && quantity.signum() >= 0, "Nonnegative measured quantities are required");
        }
        require(e.harvestedQuantity().compareTo(batch.quantity()) == 0, "Harvest evidence must match recorded lot quantity");
        require(e.qualifiedQuantity().add(e.rejectedQuantity()).compareTo(e.harvestedQuantity()) <= 0, "Grading exceeds harvested quantity");
        require(e.packedQuantity().compareTo(e.qualifiedQuantity()) <= 0 && e.shippedQuantity().compareTo(e.packedQuantity()) <= 0 &&
                e.shippedQuantity().signum() > 0, "Dispatch exceeds packed or qualified quantity");
        require(batch.harvestedAt() != null && e.packedAt() != null && e.shippedAt() != null, "Actual event dates are required");
        require(!e.packedAt().isBefore(batch.harvestedAt()) && !e.shippedAt().isBefore(e.packedAt()) &&
                !e.shippedAt().isBefore(release.releasedAt()) && !e.shippedAt().isAfter(Instant.now()), "Invalid harvest/packing/release/dispatch chronology");
        var harvestDate = batch.harvestedAt().atOffset(ZoneOffset.UTC).toLocalDate();
        require(season.startDate() != null && !harvestDate.isBefore(season.startDate()) &&
                (season.endDate() == null || !harvestDate.isAfter(season.endDate())), "Harvest date is outside season");
        require(batch.latestSafeHarvestDate() == null || !harvestDate.isBefore(batch.latestSafeHarvestDate()), "Harvest precedes safe date");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public record Summary(String status, int confirmedLotCount, Map<String, Quantities> quantitiesByUnit,
            Map<String, Map<String, BigDecimal>> shippedByMonth, List<String> excludedDossierIds) { }

    public record Quantities(BigDecimal harvested, BigDecimal qualified, BigDecimal rejected, BigDecimal packed,
            BigDecimal shipped, BigDecimal qualityPassRate) {
        Quantities add(Quantities other) {
            return new Quantities(harvested.add(other.harvested), qualified.add(other.qualified), rejected.add(other.rejected),
                    packed.add(other.packed), shipped.add(other.shipped), null);
        }
        Quantities withRate() {
            return new Quantities(harvested, qualified, rejected, packed, shipped,
                    harvested.signum() == 0 ? null : qualified.multiply(BigDecimal.valueOf(100)).divide(harvested, 4, RoundingMode.HALF_UP));
        }
    }
}
