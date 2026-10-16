package com.duriancare.farm.controller;

import com.duriancare.farm.dto.AgronomistInvitationResponse;
import com.duriancare.farm.dto.AgronomistSummaryResponse;
import com.duriancare.farm.dto.AuthorizedFarmResponse;
import com.duriancare.farm.dto.CreateAgronomistInvitationRequest;
import com.duriancare.farm.dto.FarmAuthorizationResponse;
import com.duriancare.farm.dto.MessageResponse;
import com.duriancare.farm.dto.RejectAgronomistInvitationRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.UpdateFarmAuthorizationRequest;
import com.duriancare.farm.service.AgronomistAuthorizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgronomistAuthorizationController {

    private final AgronomistAuthorizationService service;
    private final RequestActorResolver actorResolver;

    public AgronomistAuthorizationController(
            AgronomistAuthorizationService service,
            RequestActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/agronomists")
    public List<AgronomistSummaryResponse> searchAgronomists(
            @RequestParam(required = false) String query,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.searchAgronomists(actor(userId, email, role), query);
    }

    @PostMapping("/farms/{farmId}/agronomist-invitations")
    public ResponseEntity<AgronomistInvitationResponse> createInvitation(
            @PathVariable String farmId,
            @Valid @RequestBody CreateAgronomistInvitationRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createInvitation(actor(userId, email, role), farmId, request));
    }

    @GetMapping("/farms/{farmId}/agronomist-invitations")
    public List<AgronomistInvitationResponse> listFarmInvitations(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listFarmInvitations(actor(userId, email, role), farmId);
    }

    @DeleteMapping("/agronomist-invitations/{invitationId}")
    public MessageResponse cancelInvitation(
            @PathVariable String invitationId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        service.cancelInvitation(actor(userId, email, role), invitationId);
        return new MessageResponse("Invitation cancelled");
    }

    @GetMapping("/farms/{farmId}/agronomist-authorizations")
    public List<FarmAuthorizationResponse> listFarmAuthorizations(
            @PathVariable String farmId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listFarmAuthorizations(actor(userId, email, role), farmId);
    }

    @PatchMapping("/farm-authorizations/{authorizationId}")
    public FarmAuthorizationResponse updateAuthorization(
            @PathVariable String authorizationId,
            @Valid @RequestBody UpdateFarmAuthorizationRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.updateAuthorization(actor(userId, email, role), authorizationId, request);
    }

    @DeleteMapping("/farm-authorizations/{authorizationId}")
    public MessageResponse revokeAuthorization(
            @PathVariable String authorizationId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        service.revokeAuthorization(actor(userId, email, role), authorizationId);
        return new MessageResponse("Authorization revoked");
    }

    @GetMapping("/me/agronomist-invitations")
    public List<AgronomistInvitationResponse> listMyInvitations(
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listMyInvitations(actor(userId, email, role));
    }

    @PostMapping("/agronomist-invitations/{invitationId}/accept")
    public FarmAuthorizationResponse acceptInvitation(
            @PathVariable String invitationId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.acceptInvitation(actor(userId, email, role), invitationId);
    }

    @PostMapping("/agronomist-invitations/{invitationId}/reject")
    public AgronomistInvitationResponse rejectInvitation(
            @PathVariable String invitationId,
            @Valid @RequestBody RejectAgronomistInvitationRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.rejectInvitation(actor(userId, email, role), invitationId, request);
    }

    @GetMapping("/me/authorized-farms")
    public List<AuthorizedFarmResponse> listAuthorizedFarms(
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listAuthorizedFarms(actor(userId, email, role));
    }

    private RequestActor actor(String userId, String email, String role) {
        return actorResolver.resolve(userId, email, role);
    }
}
