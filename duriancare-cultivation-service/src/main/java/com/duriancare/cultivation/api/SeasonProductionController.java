package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.security.CultivationActor;
import com.duriancare.cultivation.service.SeasonProductionService;
import org.springframework.web.bind.annotation.*;

@RestController
public class SeasonProductionController {
    private final SeasonProductionService service;
    public SeasonProductionController(SeasonProductionService service) {this.service=service;}
    @PatchMapping("/api/v1/cultivation-seasons/{id}/production-status")
    public CultivationSeason transition(@PathVariable String id,@RequestBody Request request,
            @RequestHeader("X-Auth-User-Id") String userId,@RequestHeader("X-Auth-Role") String role) {
        return service.transition(new CultivationActor(userId,"",role),id,request.status());
    }
    public record Request(SeasonProductionStatus status) { }
}
