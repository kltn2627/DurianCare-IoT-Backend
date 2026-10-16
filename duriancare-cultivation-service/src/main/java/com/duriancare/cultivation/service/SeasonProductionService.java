package com.duriancare.cultivation.service;

import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.security.*;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Explicit production lifecycle, never implicitly changed by release or dispatch. */
@Service
public class SeasonProductionService {
    private final CultivationSeasonRepository seasons;
    private final HarvestBatchRepository batches;
    private final CultivationAccessGuard guard;
    public SeasonProductionService(CultivationSeasonRepository seasons, HarvestBatchRepository batches, CultivationAccessGuard guard) {
        this.seasons=seasons; this.batches=batches; this.guard=guard;
    }
    public CultivationSeason transition(CultivationActor actor, String id, SeasonProductionStatus status) {
        CultivationSeason season=seasons.findById(id).orElseThrow(()->new NoSuchElementException("Season not found"));
        guard.requireUpdate(actor,"CultivationSeason",id,season.farmId(),season.plotId());
        SeasonProductionStatus current=season.productionStatus()==null?SeasonProductionStatus.IN_PROGRESS:season.productionStatus();
        if(status==current)return season;
        if(!((current==SeasonProductionStatus.IN_PROGRESS && status==SeasonProductionStatus.HARVESTED) ||
                (current==SeasonProductionStatus.HARVESTED && status==SeasonProductionStatus.COMPLETED))) throw new IllegalStateException("Invalid season production transition");
        boolean hasHarvest=season.startDate()!=null && batches.findByCultivationSeasonId(id).stream().anyMatch(batch->batch.status()==HarvestBatchStatus.HARVESTED &&
                !"DEMO_SYNTHETIC".equals(batch.dataOrigin()) &&
                Objects.equals(batch.farmId(),season.farmId()) && Objects.equals(batch.plotId(),season.plotId()) &&
                batch.harvestedAt()!=null && !batch.harvestedAt().isAfter(Instant.now()) &&
                batch.quantity()!=null && batch.quantity().signum()>0 &&
                !batch.harvestedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().isBefore(season.startDate()) &&
                (season.endDate()==null || !batch.harvestedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(season.endDate())));
        if(!hasHarvest)throw new IllegalStateException("A matching persisted harvested lot is required");
        return seasons.save(new CultivationSeason(season.id(),season.farmId(),season.plotId(),season.name(),season.crop(),season.variety(),
                season.startDate(),season.endDate(),season.createdBy(),season.createdAt(),Instant.now(),status));
    }
}
