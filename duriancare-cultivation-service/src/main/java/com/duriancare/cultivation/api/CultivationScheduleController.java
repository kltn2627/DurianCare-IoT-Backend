package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.CultivationTaskStatus;
import com.duriancare.cultivation.domain.CultivationTaskType;
import com.duriancare.cultivation.service.CultivationScheduleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cultivation-schedules")
public class CultivationScheduleController {

    private final CultivationScheduleService service;

    public CultivationScheduleController(CultivationScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CultivationScheduleResponse> list(
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) CultivationTaskType type,
            @RequestParam(required = false) CultivationTaskStatus status) {
        return service.findAll(service.normalizeSearch(zoneId), type, status)
                .stream()
                .map(CultivationScheduleResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CultivationScheduleResponse get(@PathVariable UUID id) {
        return CultivationScheduleResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<CultivationScheduleResponse> create(@Valid @RequestBody CreateCultivationScheduleRequest request) {
        CultivationScheduleResponse response = CultivationScheduleResponse.from(service.create(request));
        return ResponseEntity.created(URI.create("/api/cultivation-schedules/" + response.id())).body(response);
    }

    @PatchMapping("/{id}/status")
    public CultivationScheduleResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCultivationStatusRequest request) {
        return CultivationScheduleResponse.from(service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
