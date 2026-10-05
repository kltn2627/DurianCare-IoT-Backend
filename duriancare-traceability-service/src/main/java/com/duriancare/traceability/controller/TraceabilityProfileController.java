package com.duriancare.traceability.controller;

import com.duriancare.traceability.domain.TraceabilityProfile;
import com.duriancare.traceability.domain.TraceabilitySnapshot;
import com.duriancare.traceability.domain.TraceabilityStatus;
import com.duriancare.traceability.repository.TraceabilityProfileRepository;
import com.duriancare.traceability.repository.TraceabilitySnapshotRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traceability")
public class TraceabilityProfileController {

    private final TraceabilityProfileRepository profileRepository;
    private final TraceabilitySnapshotRepository snapshotRepository;

    public TraceabilityProfileController(
            TraceabilityProfileRepository profileRepository,
            TraceabilitySnapshotRepository snapshotRepository) {
        this.profileRepository = profileRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @GetMapping("/profiles")
    List<TraceabilityProfile> listProfiles(
            @RequestParam(required = false) TraceabilityStatus status) {
        if (status != null) {
            return profileRepository.findAll().stream()
                    .filter(p -> p.status() == status)
                    .toList();
        }
        return profileRepository.findAll();
    }

    @GetMapping("/profiles/{id}")
    ResponseEntity<TraceabilityProfile> getProfile(@PathVariable String id) {
        return profileRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profiles/public/{slug}")
    ResponseEntity<TraceabilityProfile> getBySlug(@PathVariable String slug) {
        return profileRepository.findByPublicSlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profiles/{profileId}/snapshots")
    List<TraceabilitySnapshot> getSnapshots(@PathVariable String profileId) {
        return snapshotRepository.findByTraceabilityProfileIdOrderByVersionDesc(profileId);
    }
}
