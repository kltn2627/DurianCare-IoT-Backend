package com.duriancare.traceability.controller;

import com.duriancare.traceability.domain.ProtocolStatus;
import com.duriancare.traceability.domain.TreatmentProtocol;
import com.duriancare.traceability.repository.TreatmentProtocolRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traceability/protocols")
public class TreatmentProtocolController {

    private final TreatmentProtocolRepository protocolRepository;

    public TreatmentProtocolController(TreatmentProtocolRepository protocolRepository) {
        this.protocolRepository = protocolRepository;
    }

    @GetMapping
    List<TreatmentProtocol> listProtocols(
            @RequestParam(required = false) String farmZoneId,
            @RequestParam(required = false) ProtocolStatus status) {
        if (farmZoneId != null && status != null) {
            return protocolRepository.findByFarmZoneIdAndStatus(farmZoneId, status);
        }
        return protocolRepository.findAll();
    }

    @GetMapping("/{id}")
    ResponseEntity<TreatmentProtocol> getProtocol(@PathVariable String id) {
        return protocolRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
