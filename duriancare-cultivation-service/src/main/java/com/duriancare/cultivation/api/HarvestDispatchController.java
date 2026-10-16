package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.HarvestDispatch;
import com.duriancare.cultivation.security.CultivationActor;
import com.duriancare.cultivation.security.CultivationAuthenticationException;
import com.duriancare.cultivation.service.HarvestDispatchService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/harvest-batches")
public class HarvestDispatchController {
    private final HarvestDispatchService service;
    public HarvestDispatchController(HarvestDispatchService service) { this.service = service; }

    @GetMapping("/{id}/dispatch")
    HarvestDispatch get(@PathVariable String id, @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String role) {
        return service.get(actor(userId, role), id);
    }

    @PostMapping("/{id}/dispatch")
    HarvestDispatch record(@PathVariable String id, @RequestBody RecordRequest request,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String role) {
        return service.record(actor(userId, role), id, request.exportReleaseId(), request.evidence());
    }

    @PostMapping("/{id}/dispatch/confirm")
    HarvestDispatch confirm(@PathVariable String id, @RequestBody ConfirmRequest request,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String role) {
        try {
            return service.confirm(actor(userId, role), id, request.sourceDocumentsChecked());
        } catch (OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatch changed; reload before retrying");
        }
    }

    @GetMapping("/dispatch-summary")
    HarvestDispatchService.Summary summary(@RequestParam String farmId, @RequestParam(required = false) String cultivationSeasonId,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String role) {
        return service.summary(actor(userId, role), farmId, cultivationSeasonId);
    }

    @PostMapping("/{id}/dispatch/delivered")
    HarvestDispatch delivered(@PathVariable String id, @RequestBody DeliveryRequest request,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Role", required = false) String role) {
        try {
            return service.delivered(actor(userId, role), id, request.sourceRef(), request.deliveredAt());
        } catch (OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatch changed; reload before retrying");
        }
    }

    public record DeliveryRequest(String sourceRef, java.time.Instant deliveredAt) { }

    private static CultivationActor actor(String userId, String role) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(role)) {
            throw new CultivationAuthenticationException("Authenticated identity is required");
        }
        return new CultivationActor(userId.trim(), "", role.trim());
    }

    public record RecordRequest(String exportReleaseId, HarvestDispatch.Evidence evidence) { }
    public record ConfirmRequest(boolean sourceDocumentsChecked) { }
}
