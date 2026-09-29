package com.duriancare.cultivation.service;

import com.duriancare.cultivation.api.CultivationCalendarDtos;
import com.duriancare.cultivation.domain.CultivationSeason;
import com.duriancare.cultivation.repository.CultivationSeasonRepository;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import com.duriancare.cultivation.security.CultivationActor;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CultivationSeasonCatalogService {

    private final CultivationSeasonRepository seasonRepository;
    private final CultivationAccessGuard accessGuard;

    public CultivationSeasonCatalogService(
            CultivationSeasonRepository seasonRepository,
            CultivationAccessGuard accessGuard) {
        this.seasonRepository = seasonRepository;
        this.accessGuard = accessGuard;
    }

    public List<CultivationSeason> listSeasons(CultivationActor actor, String farmId, String plotId) {
        String normalizedFarmId = requireText(farmId, "Farm id is required");
        String normalizedPlotId = normalizeOptional(plotId);
        accessGuard.requireView(actor, "CultivationSeason", null, normalizedFarmId, normalizedPlotId);
        return StringUtils.hasText(normalizedPlotId)
                ? seasonRepository.findByFarmIdAndPlotIdOrderByStartDateDesc(normalizedFarmId, normalizedPlotId)
                : seasonRepository.findByFarmIdOrderByStartDateDesc(normalizedFarmId);
    }

    public CultivationSeason getSeason(CultivationActor actor, String seasonId) {
        CultivationSeason season = requireSeason(seasonId);
        accessGuard.requireView(actor, "CultivationSeason", season.id(), season.farmId(), season.plotId());
        return season;
    }

    public CultivationSeason createSeason(
            CultivationActor actor,
            CultivationCalendarDtos.CreateCultivationSeasonRequest request) {
        String farmId = requireText(request.farmId(), "Farm id is required");
        String plotId = requireText(request.plotId(), "Plot id is required");
        validateDateRange(request.startDate(), request.endDate());
        accessGuard.requireCreate(actor, "CultivationSeason", null, farmId, plotId);
        Instant now = Instant.now();
        return seasonRepository.save(new CultivationSeason(
                null,
                farmId,
                plotId,
                requireText(request.name(), "Season name is required"),
                normalizeOptional(request.crop()),
                normalizeOptional(request.variety()),
                request.startDate(),
                request.endDate(),
                requireText(request.createdBy(), "Created by is required"),
                now,
                now));
    }

    public CultivationSeason updateSeason(
            CultivationActor actor,
            String seasonId,
            CultivationCalendarDtos.UpdateCultivationSeasonRequest request) {
        CultivationSeason season = requireSeason(seasonId);
        accessGuard.requireUpdate(actor, "CultivationSeason", season.id(), season.farmId(), season.plotId());
        validateDateRange(
                request.startDate() == null ? season.startDate() : request.startDate(),
                request.endDate() == null ? season.endDate() : request.endDate());
        Instant now = Instant.now();
        return seasonRepository.save(new CultivationSeason(
                season.id(),
                season.farmId(),
                season.plotId(),
                StringUtils.hasText(request.name()) ? request.name().trim() : season.name(),
                request.crop() == null ? season.crop() : normalizeOptional(request.crop()),
                request.variety() == null ? season.variety() : normalizeOptional(request.variety()),
                request.startDate() == null ? season.startDate() : request.startDate(),
                request.endDate() == null ? season.endDate() : request.endDate(),
                season.createdBy(),
                season.createdAt(),
                now));
    }

    private CultivationSeason requireSeason(String seasonId) {
        return seasonRepository.findById(requireText(seasonId, "Season id is required"))
                .orElseThrow(() -> new NoSuchElementException("Cultivation season was not found"));
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Season start date is required");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Season end date must not be before start date");
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
