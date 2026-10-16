package com.duriancare.farm.controller;

import com.duriancare.farm.dto.FarmAccessCheckRequest;
import com.duriancare.farm.dto.FarmAccessCheckResponse;
import com.duriancare.farm.dto.FarmAlertRecipientRequest;
import com.duriancare.farm.dto.FarmAlertRecipientResponse;
import com.duriancare.farm.service.FarmAccessCheckService;
import com.duriancare.farm.service.FarmAuthenticationException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/farm-access")
public class InternalFarmAccessController {

    private final FarmAccessCheckService service;
    private final String internalToken;

    public InternalFarmAccessController(
            FarmAccessCheckService service,
            @Value("${duriancare.internal.auth-token:${INTERNAL_SERVICE_TOKEN:local-internal-token}}")
            String internalToken) {
        this.service = service;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @PostMapping("/check")
    public FarmAccessCheckResponse check(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody FarmAccessCheckRequest request) {
        requireInternalToken(token);
        return new FarmAccessCheckResponse(service.canAccess(request));
    }

    @PostMapping("/alert-recipients")
    public FarmAlertRecipientResponse alertRecipients(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody FarmAlertRecipientRequest request) {
        requireInternalToken(token);
        return new FarmAlertRecipientResponse(service.alertRecipients(
                request.farmId(),
                request.cultivationAreaId(),
                request.permission()));
    }

    private void requireInternalToken(String token) {
        if (StringUtils.hasText(internalToken) && !internalToken.equals(token)) {
            throw new FarmAuthenticationException("Internal service token is invalid");
        }
    }
}
