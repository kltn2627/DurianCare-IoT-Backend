package com.duriancare.farm.service;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.ZoneStatus;
import com.duriancare.farm.dto.FarmCatalogDtos;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.repository.FarmAuthorizationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FarmCatalogService {

    private final FarmRepository farmRepository;
    private final FarmAuthorizationRepository authorizationRepository;
    private final AgronomistRolePolicy rolePolicy;

    public FarmCatalogService(FarmRepository farmRepository, FarmAuthorizationRepository authorizationRepository,
            AgronomistRolePolicy rolePolicy) {
        this.farmRepository = farmRepository;
        this.authorizationRepository = authorizationRepository;
        this.rolePolicy = rolePolicy;
    }

    public List<FarmCatalogDtos.FarmResponse> listCalendarFarms(RequestActor actor) {
        if (rolePolicy.isOwner(actor.role())) return listOwnedFarms(actor);
        if ("ADMIN".equals(rolePolicy.normalize(actor.role()))) {
            return farmRepository.findAll().stream()
                    .filter(farm -> farm.status() != FarmStatus.ARCHIVED)
                    .sorted(Comparator.comparing(Farm::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .map(this::toResponse)
                    .toList();
        }
        if (!rolePolicy.isAgronomist(actor.role())) return List.of();
        var zonesByFarm = new LinkedHashMap<String, Set<String>>();
        var unrestrictedFarms = new HashSet<String>();
        Instant now = Instant.now();
        authorizationRepository.findByAgronomistIdAndStatus(actor.userId(), AuthorizationStatus.ACTIVE).stream()
                .filter(authorization -> authorization.validUntil() == null || authorization.validUntil().isAfter(now))
                .filter(authorization -> authorization.permissions() != null
                        && authorization.permissions().contains(FarmPermissionType.VIEW_CARE_SCHEDULE))
                .forEach(authorization -> {
                    zonesByFarm.computeIfAbsent(authorization.farmId(), ignored -> new HashSet<>());
                    if (authorization.allowedCultivationAreaIds().isEmpty()) {
                        unrestrictedFarms.add(authorization.farmId());
                    } else {
                        zonesByFarm.get(authorization.farmId()).addAll(authorization.allowedCultivationAreaIds());
                    }
                });
        return zonesByFarm.entrySet().stream()
                .map(entry -> farmRepository.findById(entry.getKey())
                        .filter(farm -> farm.status() != FarmStatus.ARCHIVED)
                        .map(farm -> {
                            var response = toResponse(farm);
                            var allowedZones = unrestrictedFarms.contains(entry.getKey())
                                    ? response.zones()
                                    : response.zones().stream().filter(zone -> entry.getValue().contains(zone.id())).toList();
                            return new FarmCatalogDtos.FarmResponse(response.id(), response.ownerUserId(),
                                    response.name(), response.address(), response.province(), response.district(),
                                    response.latitude(), response.longitude(), response.areaHectares(), response.status(),
                                    allowedZones, response.createdAt(), response.updatedAt());
                        }).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
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
                request.status() == null ? ZoneStatus.ACTIVE : request.status(),
                now,
                now,
                request.agronomy());
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
                now,
                request.agronomy() == null ? current.agronomy() : request.agronomy());
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
                zone.updatedAt(),
                zone.agronomy());
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
