package com.duriancare.farm.controller;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.dto.FarmSummaryResponse;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.ZoneSummaryResponse;
import com.duriancare.farm.repository.DurianTreeRepository;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.service.FarmNotFoundException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farms")
public class FarmController {

    private final FarmRepository farmRepository;
    private final DurianTreeRepository treeRepository;
    private final RequestActorResolver actorResolver;

    public FarmController(
            FarmRepository farmRepository,
            DurianTreeRepository treeRepository,
            RequestActorResolver actorResolver) {
        this.farmRepository = farmRepository;
        this.treeRepository = treeRepository;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<FarmSummaryResponse> listFarms(
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        RequestActor actor = actorResolver.resolve(userId, email, role);
        return farmRepository.findByOwnerUserId(actor.userId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/{farmId}")
    public FarmSummaryResponse getFarm(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        RequestActor actor = actorResolver.resolve(userId, email, role);
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new FarmNotFoundException("Farm not found: " + farmId));
        if (!farm.ownerUserId().equals(actor.userId())) {
            throw new com.duriancare.farm.service.FarmAccessDeniedException(
                    "Access denied to farm: " + farmId);
        }
        return toSummary(farm);
    }

    @GetMapping("/{farmId}/zones")
    public List<ZoneSummaryResponse> listZones(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        RequestActor actor = actorResolver.resolve(userId, email, role);
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new FarmNotFoundException("Farm not found: " + farmId));
        if (!farm.ownerUserId().equals(actor.userId())) {
            throw new com.duriancare.farm.service.FarmAccessDeniedException(
                    "Access denied to farm: " + farmId);
        }
        List<FarmZone> zones = farm.zones() != null ? farm.zones() : List.of();
        return zones.stream()
                .map(z -> new ZoneSummaryResponse(
                        z.id(), z.name(), z.code(), z.areaSquareMeters(),
                        z.status() != null ? z.status().name() : null,
                        treeRepository.countByFarmZoneId(z.id())))
                .toList();
    }

    private FarmSummaryResponse toSummary(Farm farm) {
        int zoneCount = farm.zones() != null ? farm.zones().size() : 0;
        return new FarmSummaryResponse(
                farm.id(), farm.name(), farm.address(), farm.province(), farm.district(),
                farm.areaHectares(),
                farm.status() != null ? farm.status().name() : null,
                zoneCount, farm.createdAt());
    }
}
