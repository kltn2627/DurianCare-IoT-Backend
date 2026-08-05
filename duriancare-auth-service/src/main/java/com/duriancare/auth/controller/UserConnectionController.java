package com.duriancare.auth.controller;

import com.duriancare.auth.dto.ConnectionPageResponse;
import com.duriancare.auth.dto.ConnectionUserSummary;
import com.duriancare.auth.dto.CreateConnectionRequest;
import com.duriancare.auth.dto.UserConnectionResponse;
import com.duriancare.auth.exception.InvalidTokenException;
import com.duriancare.auth.security.AuthenticatedUser;
import com.duriancare.auth.service.UserConnectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping
public class UserConnectionController {

    private final UserConnectionService connectionService;

    public UserConnectionController(UserConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/api/connections/search")
    ConnectionUserSummary search(
            Principal principal,
            @RequestParam
            @Pattern(regexp = "^[0-9+() .-]{8,30}$", message = "Phone number format is invalid")
            String phoneNumber) {
        return connectionService.searchByPhone(resolveUserId(principal), phoneNumber);
    }

    @GetMapping("/api/community/users")
    ConnectionPageResponse<ConnectionUserSummary> communityUsers(
            Principal principal,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return connectionService.communityUsers(resolveUserId(principal), query, page, size);
    }

    @PostMapping("/api/connections/requests")
    UserConnectionResponse createRequest(
            Principal principal,
            @Valid @RequestBody CreateConnectionRequest request) {
        return connectionService.createRequest(resolveUserId(principal), request);
    }

    @GetMapping("/api/connections/requests/incoming")
    List<UserConnectionResponse> incoming(Principal principal) {
        return connectionService.incoming(resolveUserId(principal));
    }

    @GetMapping("/api/connections/requests/outgoing")
    List<UserConnectionResponse> outgoing(Principal principal) {
        return connectionService.outgoing(resolveUserId(principal));
    }

    @PatchMapping("/api/connections/requests/{connectionId}/accept")
    UserConnectionResponse accept(Principal principal, @PathVariable UUID connectionId) {
        return connectionService.accept(resolveUserId(principal), connectionId);
    }

    @PatchMapping("/api/connections/requests/{connectionId}/reject")
    UserConnectionResponse reject(Principal principal, @PathVariable UUID connectionId) {
        return connectionService.reject(resolveUserId(principal), connectionId);
    }

    @PatchMapping("/api/connections/requests/{connectionId}/cancel")
    UserConnectionResponse cancel(Principal principal, @PathVariable UUID connectionId) {
        return connectionService.cancel(resolveUserId(principal), connectionId);
    }

    @GetMapping("/api/connections")
    ConnectionPageResponse<UserConnectionResponse> connections(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return connectionService.connections(resolveUserId(principal), page, size);
    }

    @DeleteMapping("/api/connections/{connectionId}")
    UserConnectionResponse disconnect(Principal principal, @PathVariable UUID connectionId) {
        return connectionService.disconnect(resolveUserId(principal), connectionId);
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.userId();
        }
        throw new InvalidTokenException("Authenticated user is required");
    }
}
