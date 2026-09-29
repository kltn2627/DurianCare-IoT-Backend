package com.duriancare.farm.service;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.ZoneStatus;
import com.duriancare.farm.dto.FarmCatalogDtos;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.repository.FarmRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FarmCatalogService {

    private final FarmRepository farmRepository;
    private final AgronomistRolePolicy rolePolicy;

    public FarmCatalogService(FarmRepository farmRepository, AgronomistRolePolicy rolePolicy) {
        this.farmRepository = farmRepository;
        this.rolePolicy = rolePolicy;
    }

    public List<FarmCatalogDtos.FarmResponse> listOwnedFarms(RequestActor actor) {
        requireOwner(actor);
        return farmRepository.findByOwnerUserIdAndStatusNot(actor.userId(), FarmStatus.ARCHIVED).stream()
                .sorted(Comparator.comparing(Farm::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toResponse)
                .toList();
    }

    public FarmCatalogDtos.FarmResponse createFarm(
            RequestActor actor,
            FarmCatalogDtos.CreateFarmRequest request) {
        requireOwner(actor);
        Instant now = Instant.now();
        Farm farm = farmRepository.save(new Farm(
                null,
                actor.userId(),
                requireText(request.name(), "Farm name is required"),
                normalizeOptional(request.address()),
                normalizeOptional(request.province()),
                normalizeOptional(request.district()),
                request.latitude(),
                request.longitude(),
                request.areaHectares(),
                FarmStatus.ACTIVE,
                List.of(),
                now,
                now));
        return toResponse(farm);
    }

    public FarmCatalogDtos.FarmResponse getOwnedFarm(RequestActor actor, String farmId) {
        return toResponse(requireOwnedFarm(actor, farmId));
    }

    public FarmCatalogDtos.FarmResponse updateFarm(
            RequestActor actor,
            String farmId,
            FarmCatalogDtos.UpdateFarmRequest request) {
        Farm farm = requireOwnedFarm(actor, farmId);
        Instant now = Instant.now();
        Farm updated = farmRepository.save(new Farm(
                farm.id(),
                farm.ownerUserId(),
                StringUtils.hasText(request.name()) ? request.name().trim() : farm.name(),
                request.address() == null ? farm.address() : normalizeOptional(request.address()),
                request.province() == null ? farm.province() : normalizeOptional(request.province()),
                request.district() == null ? farm.district() : normalizeOptional(request.district()),
                request.latitude() == null ? farm.latitude() : request.latitude(),
                request.longitude() == null ? farm.longitude() : request.longitude(),
                request.areaHectares() == null ? farm.areaHectares() : request.areaHectares(),
                request.status() == null ? farm.status() : request.status(),
                zonesOf(farm),
                farm.createdAt(),
                now));
        return toResponse(updated);
    }

    public void archiveFarm(RequestActor actor, String farmId) {
        Farm farm = requireOwnedFarm(actor, farmId);
        Instant now = Instant.now();
        farmRepository.save(new Farm(
                farm.id(),
                farm.ownerUserId(),
                farm.name(),
                farm.address(),
                farm.province(),
                farm.district(),
                farm.latitude(),
                farm.longitude(),
                farm.areaHectares(),
                FarmStatus.ARCHIVED,
                zonesOf(farm),
                farm.createdAt(),
                now));
    }

    public List<FarmCatalogDtos.FarmZoneResponse> listZones(RequestActor actor, String farmId) {
        Farm farm = requireOwnedFarm(actor, farmId);
        return activeZonesOf(farm).stream().map(this::toZoneResponse).toList();
    }

    public FarmCatalogDtos.FarmZoneResponse createZone(
            RequestActor actor,
            String farmId,
            FarmCatalogDtos.CreateFarmZoneRequest request) {
        Farm farm = requireOwnedFarm(actor, farmId);
        Instant now = Instant.now();
        FarmZone zone = new FarmZone(
                UUID.randomUUID().toString(),
                requireText(request.name(), "Zone name is required"),
                normalizeOptional(request.code()),
                request.areaSquareMeters(),
                request.boundaryGeoJson(),
                normalizeOptional(request.description()),
                ZoneStatus.ACTIVE,
                now,
                now);
        List<FarmZone> zones = new ArrayList<>(zonesOf(farm));
        zones.add(zone);
        farmRepository.save(copyWithZones(farm, zones, now));
        return toZoneResponse(zone);
    }

    public FarmCatalogDtos.FarmZoneResponse updateZone(
            RequestActor actor,
            String farmId,
            String zoneId,
            FarmCatalogDtos.UpdateFarmZoneRequest request) {
        Farm farm = requireOwnedFarm(actor, farmId);
        Instant now = Instant.now();
        List<FarmZone> zones = new ArrayList<>(zonesOf(farm));
        int index = indexOfZone(zones, zoneId);
        FarmZone current = zones.get(index);
        FarmZone updated = new FarmZone(
                current.id(),
                StringUtils.hasText(request.name()) ? request.name().trim() : current.name(),
                request.code() == null ? current.code() : normalizeOptional(request.code()),
                request.areaSquareMeters() == null ? current.areaSquareMeters() : request.areaSquareMeters(),
                request.boundaryGeoJson() == null ? current.boundaryGeoJson() : request.boundaryGeoJson(),
                request.description() == null ? current.description() : normalizeOptional(request.description()),
                request.status() == null ? current.status() : request.status(),
                current.createdAt(),
                now);
        zones.set(index, updated);
        farmRepository.save(copyWithZones(farm, zones, now));
        return toZoneResponse(updated);
    }

    public void archiveZone(RequestActor actor, String farmId, String zoneId) {
        updateZone(actor, farmId, zoneId, new FarmCatalogDtos.UpdateFarmZoneRequest(
                null,
                null,
                null,
                null,
                null,
                ZoneStatus.ARCHIVED));
    }

    private Farm requireOwnedFarm(RequestActor actor, String farmId) {
        requireOwner(actor);
        Farm farm = farmRepository.findById(requireText(farmId, "Farm id is required"))
                .orElseThrow(() -> new FarmNotFoundException("Farm was not found"));
        if (!Objects.equals(farm.ownerUserId(), actor.userId()) || farm.status() == FarmStatus.ARCHIVED) {
            throw new FarmAccessDeniedException("Only the farm owner can access this farm");
        }
        return farm;
    }

    private void requireOwner(RequestActor actor) {
        if (actor == null || !StringUtils.hasText(actor.userId())) {
            throw new FarmAuthenticationException("Authenticated user is required");
        }
        if (!rolePolicy.isOwner(actor.role())) {
            throw new FarmAccessDeniedException("Farmer role is required");
        }
    }

    private int indexOfZone(List<FarmZone> zones, String zoneId) {
        String normalized = requireText(zoneId, "Zone id is required");
        for (int index = 0; index < zones.size(); index++) {
            if (normalized.equals(zones.get(index).id())) {
                return index;
            }
        }
        throw new FarmNotFoundException("Farm zone was not found");
    }

    private Farm copyWithZones(Farm farm, List<FarmZone> zones, Instant updatedAt) {
        return new Farm(
                farm.id(),
                farm.ownerUserId(),
                farm.name(),
                farm.address(),
                farm.province(),
                farm.district(),
                farm.latitude(),
                farm.longitude(),
                farm.areaHectares(),
                farm.status(),
                List.copyOf(zones),
                farm.createdAt(),
                updatedAt);
    }

    private List<FarmZone> zonesOf(Farm farm) {
        return farm.zones() == null ? List.of() : farm.zones();
    }

    private List<FarmZone> activeZonesOf(Farm farm) {
        return zonesOf(farm).stream()
                .filter(zone -> zone.status() != ZoneStatus.ARCHIVED)
                .toList();
    }

    private FarmCatalogDtos.FarmResponse toResponse(Farm farm) {
        return new FarmCatalogDtos.FarmResponse(
                farm.id(),
                farm.ownerUserId(),
                farm.name(),
                farm.address(),
                farm.province(),
                farm.district(),
                farm.latitude(),
                farm.longitude(),
                farm.areaHectares(),
                farm.status(),
                activeZonesOf(farm).stream().map(this::toZoneResponse).toList(),
                farm.createdAt(),
                farm.updatedAt());
    }

    private FarmCatalogDtos.FarmZoneResponse toZoneResponse(FarmZone zone) {
        return new FarmCatalogDtos.FarmZoneResponse(
                zone.id(),
                zone.name(),
                zone.code(),
                zone.areaSquareMeters(),
                zone.boundaryGeoJson(),
                zone.description(),
                zone.status(),
                zone.createdAt(),
                zone.updatedAt());
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new FarmInvalidRequestException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
