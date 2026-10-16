package com.duriancare.farm.controller;

import com.duriancare.farm.dto.FarmCatalogDtos;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.service.FarmCatalogService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/farms")
public class FarmCatalogController {

    private final FarmCatalogService service;
    private final RequestActorResolver actorResolver;

    public FarmCatalogController(FarmCatalogService service, RequestActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<FarmCatalogDtos.FarmResponse> listOwnedFarms(
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listOwnedFarms(actor(userId, email, role));
    }

    @PostMapping
    public ResponseEntity<FarmCatalogDtos.FarmResponse> createFarm(
            @Valid @RequestBody FarmCatalogDtos.CreateFarmRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        FarmCatalogDtos.FarmResponse farm = service.createFarm(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/farms/" + farm.id())).body(farm);
    }

    @GetMapping("/{farmId}")
    public FarmCatalogDtos.FarmResponse getFarm(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getOwnedFarm(actor(userId, email, role), farmId);
    }

    @PatchMapping("/{farmId}")
    public FarmCatalogDtos.FarmResponse updateFarm(
            @PathVariable String farmId,
            @Valid @RequestBody FarmCatalogDtos.UpdateFarmRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.updateFarm(actor(userId, email, role), farmId, request);
    }

    @DeleteMapping("/{farmId}")
    public ResponseEntity<Void> archiveFarm(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        service.archiveFarm(actor(userId, email, role), farmId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{farmId}/zones")
    public List<FarmCatalogDtos.FarmZoneResponse> listZones(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listZones(actor(userId, email, role), farmId);
    }

    @PostMapping("/{farmId}/zones")
    public ResponseEntity<FarmCatalogDtos.FarmZoneResponse> createZone(
            @PathVariable String farmId,
            @Valid @RequestBody FarmCatalogDtos.CreateFarmZoneRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        FarmCatalogDtos.FarmZoneResponse zone = service.createZone(actor(userId, email, role), farmId, request);
        return ResponseEntity.created(URI.create("/api/v1/farms/" + farmId + "/zones/" + zone.id())).body(zone);
    }

    @PatchMapping("/{farmId}/zones/{zoneId}")
    public FarmCatalogDtos.FarmZoneResponse updateZone(
            @PathVariable String farmId,
            @PathVariable String zoneId,
            @Valid @RequestBody FarmCatalogDtos.UpdateFarmZoneRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.updateZone(actor(userId, email, role), farmId, zoneId, request);
    }

    @DeleteMapping("/{farmId}/zones/{zoneId}")
    public ResponseEntity<Void> archiveZone(
            @PathVariable String farmId,
            @PathVariable String zoneId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        service.archiveZone(actor(userId, email, role), farmId, zoneId);
        return ResponseEntity.noContent().build();
    }

    private RequestActor actor(String userId, String email, String role) {
        return actorResolver.resolve(userId, email, role);
    }
}
