package com.duriancare.farm.service;

import com.duriancare.farm.domain.AgronomistInvitation;
import com.duriancare.farm.domain.AgronomistInvitationStatus;
import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmAuthorization;
import com.duriancare.farm.domain.FarmAuthorizationAuditAction;
import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.dto.AgronomistInvitationResponse;
import com.duriancare.farm.dto.AgronomistSummaryResponse;
import com.duriancare.farm.dto.AuthorizedFarmResponse;
import com.duriancare.farm.dto.CreateAgronomistInvitationRequest;
import com.duriancare.farm.dto.FarmAuthorizationResponse;
import com.duriancare.farm.dto.RejectAgronomistInvitationRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.UpdateFarmAuthorizationRequest;
import com.duriancare.farm.event.publisher.FarmNotificationEventPublisher;
import com.duriancare.farm.repository.AgronomistInvitationRepository;
import com.duriancare.farm.repository.FarmAuthorizationRepository;
import com.duriancare.farm.repository.FarmRepository;
import com.mongodb.DuplicateKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgronomistAuthorizationService {

    private static final Duration DEFAULT_INVITATION_TTL = Duration.ofDays(14);
    private static final Set<FarmPermissionType> DEFAULT_PERMISSIONS = EnumSet.of(
            FarmPermissionType.VIEW_FARM,
            FarmPermissionType.VIEW_CULTIVATION_AREA,
            FarmPermissionType.VIEW_CARE_SCHEDULE,
            FarmPermissionType.CHAT_WITH_OWNER);

    private final FarmRepository farmRepository;
    private final AgronomistInvitationRepository invitationRepository;
    private final FarmAuthorizationRepository authorizationRepository;
    private final AgronomistDirectoryClient agronomistDirectoryClient;
    private final FarmAuthorizationAuditService auditService;
    private final FarmNotificationEventPublisher notificationPublisher;
    private final AgronomistRolePolicy rolePolicy;
    private final MongoTemplate mongoTemplate;

    public AgronomistAuthorizationService(
            FarmRepository farmRepository,
            AgronomistInvitationRepository invitationRepository,
            FarmAuthorizationRepository authorizationRepository,
            AgronomistDirectoryClient agronomistDirectoryClient,
            FarmAuthorizationAuditService auditService,
            FarmNotificationEventPublisher notificationPublisher,
            AgronomistRolePolicy rolePolicy,
            MongoTemplate mongoTemplate) {
        this.farmRepository = farmRepository;
        this.invitationRepository = invitationRepository;
        this.authorizationRepository = authorizationRepository;
        this.agronomistDirectoryClient = agronomistDirectoryClient;
        this.auditService = auditService;
        this.notificationPublisher = notificationPublisher;
        this.rolePolicy = rolePolicy;
        this.mongoTemplate = mongoTemplate;
    }

    public List<AgronomistSummaryResponse> searchAgronomists(RequestActor actor, String query) {
        requireAuthenticated(actor);
        return agronomistDirectoryClient.search(query);
    }

    public AgronomistInvitationResponse createInvitation(
            RequestActor actor,
            String farmId,
            CreateAgronomistInvitationRequest request) {
        Farm farm = requireOwnedFarm(actor, farmId);
        String agronomistId = requireText(request.agronomistId(), "Agronomist id is required");
        agronomistDirectoryClient.getEligible(agronomistId);
        Instant now = Instant.now();
        Instant expiresAt = request.expiresAt() == null ? now.plus(DEFAULT_INVITATION_TTL) : request.expiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new FarmInvalidRequestException("Invitation expiry must be in the future");
        }
        if (invitationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                farm.id(), agronomistId, AgronomistInvitationStatus.PENDING)) {
            throw new FarmConflictException("A pending invitation already exists for this agronomist and farm");
        }
        if (authorizationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                farm.id(), agronomistId, AuthorizationStatus.ACTIVE)) {
            throw new FarmConflictException("This agronomist already has active authorization for the farm");
        }

        AgronomistInvitation invitation;
        try {
            invitation = invitationRepository.save(new AgronomistInvitation(
                    null,
                    farm.id(),
                    farm.ownerUserId(),
                    agronomistId,
                    AgronomistInvitationStatus.PENDING,
                    true,
                    normalizeOptionalText(request.message()),
                    normalizePermissions(request.initialPermissions()),
                    normalizeAreaScope(farm, request.initialAllowedCultivationAreaIds()),
                    now,
                    null,
                    expiresAt));
        } catch (org.springframework.dao.DuplicateKeyException | DuplicateKeyException exception) {
            throw new FarmConflictException("A pending invitation already exists for this agronomist and farm");
        }
        auditService.record(
                FarmAuthorizationAuditAction.INVITATION_CREATED,
                actor,
                farm.id(),
                agronomistId,
                invitation.id(),
                Map.of(),
                invitationSnapshot(invitation));
        publish(
                "AGRONOMIST_INVITATION_CREATED",
                agronomistId,
                "Bạn có lời mời cộng tác mới",
                "Chủ vườn đã mời bạn hỗ trợ vườn " + farm.name(),
                farm.id(),
                invitation.id(),
                null);
        return toResponse(invitation);
    }

    public List<AgronomistInvitationResponse> listFarmInvitations(RequestActor actor, String farmId) {
        Farm farm = requireOwnedFarm(actor, farmId);
        return invitationRepository.findByFarmIdAndOwnerId(farm.id(), farm.ownerUserId()).stream()
                .map(this::expireInvitationIfNeeded)
                .sorted(Comparator.comparing(AgronomistInvitation::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(this::toResponse)
                .toList();
    }

    public void cancelInvitation(RequestActor actor, String invitationId) {
        AgronomistInvitation invitation = requireInvitation(invitationId);
        Farm farm = requireOwnedFarm(actor, invitation.farmId());
        invitation = expireInvitationIfNeeded(invitation);
        if (invitation.status() != AgronomistInvitationStatus.PENDING) {
            throw new FarmConflictException("Only pending invitations can be cancelled");
        }
        AgronomistInvitation cancelled = invitationRepository.save(copyInvitation(
                invitation,
                AgronomistInvitationStatus.CANCELLED,
                Instant.now()));
        auditService.record(
                FarmAuthorizationAuditAction.INVITATION_CANCELLED,
                actor,
                farm.id(),
                cancelled.agronomistId(),
                cancelled.id(),
                invitationSnapshot(invitation),
                invitationSnapshot(cancelled));
    }

    public List<FarmAuthorizationResponse> listFarmAuthorizations(RequestActor actor, String farmId) {
        Farm farm = requireOwnedFarm(actor, farmId);
        return authorizationRepository.findByFarmIdAndOwnerId(farm.id(), farm.ownerUserId()).stream()
                .map(authorization -> expireAuthorizationIfNeeded(actor, authorization))
                .sorted(Comparator.comparing(FarmAuthorization::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(this::toResponse)
                .toList();
    }

    public FarmAuthorizationResponse updateAuthorization(
            RequestActor actor,
            String authorizationId,
            UpdateFarmAuthorizationRequest request) {
        FarmAuthorization authorization = requireAuthorization(authorizationId);
        Farm farm = requireOwnedFarm(actor, authorization.farmId());
        authorization = expireAuthorizationIfNeeded(actor, authorization);
        if (authorization.status() != AuthorizationStatus.ACTIVE) {
            throw new FarmConflictException("Only active authorizations can be updated");
        }
        Instant now = Instant.now();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new FarmInvalidRequestException("Authorization expiry must be in the future");
        }
        FarmAuthorization updated = authorizationRepository.save(new FarmAuthorization(
                authorization.id(),
                authorization.farmId(),
                authorization.ownerId(),
                authorization.agronomistId(),
                AuthorizationStatus.ACTIVE,
                true,
                normalizePermissions(request.permissions()),
                normalizeAreaScope(farm, request.allowedCultivationAreaIds()),
                authorization.grantedAt(),
                request.expiresAt(),
                null,
                authorization.createdAt(),
                now,
                authorization.version()));
        auditService.record(
                FarmAuthorizationAuditAction.AUTHORIZATION_UPDATED,
                actor,
                farm.id(),
                updated.agronomistId(),
                updated.id(),
                authorizationSnapshot(authorization),
                authorizationSnapshot(updated));
        publish(
                "FARM_AUTHORIZATION_UPDATED",
                updated.agronomistId(),
                "Quyền truy cập vườn đã được cập nhật",
                "Chủ vườn đã cập nhật quyền cộng tác tại vườn " + farm.name(),
                farm.id(),
                null,
                updated.id());
        return toResponse(updated);
    }

    public void revokeAuthorization(RequestActor actor, String authorizationId) {
        FarmAuthorization authorization = requireAuthorization(authorizationId);
        Farm farm = requireOwnedFarm(actor, authorization.farmId());
        authorization = expireAuthorizationIfNeeded(actor, authorization);
        if (authorization.status() != AuthorizationStatus.ACTIVE) {
            throw new FarmConflictException("Only active authorizations can be revoked");
        }
        Instant now = Instant.now();
        FarmAuthorization revoked = authorizationRepository.save(new FarmAuthorization(
                authorization.id(),
                authorization.farmId(),
                authorization.ownerId(),
                authorization.agronomistId(),
                AuthorizationStatus.REVOKED,
                false,
                authorization.permissions(),
                authorization.allowedCultivationAreaIds(),
                authorization.grantedAt(),
                authorization.expiresAt(),
                now,
                authorization.createdAt(),
                now,
                authorization.version()));
        auditService.record(
                FarmAuthorizationAuditAction.AUTHORIZATION_REVOKED,
                actor,
                farm.id(),
                revoked.agronomistId(),
                revoked.id(),
                authorizationSnapshot(authorization),
                authorizationSnapshot(revoked));
        publish(
                "FARM_AUTHORIZATION_REVOKED",
                revoked.agronomistId(),
                "Quyền truy cập vườn đã được thu hồi",
                "Chủ vườn đã thu hồi quyền cộng tác tại vườn " + farm.name(),
                farm.id(),
                null,
                revoked.id());
    }

    public List<AgronomistInvitationResponse> listMyInvitations(RequestActor actor) {
        requireAgronomist(actor);
        return invitationRepository.findByAgronomistIdAndStatus(actor.userId(), AgronomistInvitationStatus.PENDING)
                .stream()
                .map(this::expireInvitationIfNeeded)
                .filter(invitation -> invitation.status() == AgronomistInvitationStatus.PENDING)
                .sorted(Comparator.comparing(AgronomistInvitation::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(this::toResponse)
                .toList();
    }

    public FarmAuthorizationResponse acceptInvitation(RequestActor actor, String invitationId) {
        requireAgronomist(actor);
        AgronomistInvitation invitation = requireOwnInvitation(actor, invitationId);
        invitation = expireInvitationIfNeeded(invitation);
        if (invitation.status() != AgronomistInvitationStatus.PENDING) {
            throw new FarmConflictException("Only pending invitations can be accepted");
        }
        if (authorizationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                invitation.farmId(), invitation.agronomistId(), AuthorizationStatus.ACTIVE)) {
            throw new FarmConflictException("This farm is already authorized for the agronomist");
        }

        Instant now = Instant.now();
        AgronomistInvitation accepted = markInvitationAcceptedAtomically(actor, invitation.id(), now);
        FarmAuthorization authorization;
        try {
            authorization = authorizationRepository.save(new FarmAuthorization(
                    null,
                    accepted.farmId(),
                    accepted.ownerId(),
                    accepted.agronomistId(),
                    AuthorizationStatus.ACTIVE,
                    true,
                    accepted.initialPermissions(),
                    accepted.initialAllowedCultivationAreaIds(),
                    now,
                    accepted.expiresAt(),
                    null,
                    now,
                    now,
                    null));
        } catch (org.springframework.dao.DuplicateKeyException | DuplicateKeyException exception) {
            compensateAcceptedInvitation(accepted);
            throw new FarmConflictException("This farm is already authorized for the agronomist");
        } catch (RuntimeException exception) {
            compensateAcceptedInvitation(accepted);
            throw exception;
        }
        auditService.record(
                FarmAuthorizationAuditAction.INVITATION_ACCEPTED,
                actor,
                accepted.farmId(),
                accepted.agronomistId(),
                accepted.id(),
                invitationSnapshot(invitation),
                invitationSnapshot(accepted));
        auditService.record(
                FarmAuthorizationAuditAction.AUTHORIZATION_CREATED,
                actor,
                authorization.farmId(),
                authorization.agronomistId(),
                authorization.id(),
                Map.of(),
                authorizationSnapshot(authorization));
        Farm farm = farmRepository.findById(accepted.farmId()).orElse(null);
        publish(
                "AGRONOMIST_INVITATION_ACCEPTED",
                accepted.ownerId(),
                "Kỹ sư đã chấp nhận lời mời",
                "Kỹ sư đã chấp nhận cộng tác" + (farm == null ? "" : " tại vườn " + farm.name()),
                accepted.farmId(),
                accepted.id(),
                authorization.id());
        return toResponse(authorization);
    }

    public AgronomistInvitationResponse rejectInvitation(
            RequestActor actor,
            String invitationId,
            RejectAgronomistInvitationRequest request) {
        requireAgronomist(actor);
        AgronomistInvitation invitation = requireOwnInvitation(actor, invitationId);
        invitation = expireInvitationIfNeeded(invitation);
        if (invitation.status() != AgronomistInvitationStatus.PENDING) {
            throw new FarmConflictException("Only pending invitations can be rejected");
        }
        AgronomistInvitation rejected = invitationRepository.save(copyInvitation(
                invitation,
                AgronomistInvitationStatus.REJECTED,
                Instant.now()));
        auditService.record(
                FarmAuthorizationAuditAction.INVITATION_REJECTED,
                actor,
                rejected.farmId(),
                rejected.agronomistId(),
                rejected.id(),
                invitationSnapshot(invitation),
                rejectionSnapshot(rejected, request));
        publish(
                "AGRONOMIST_INVITATION_REJECTED",
                invitation.ownerId(),
                "Kỹ sư đã từ chối lời mời",
                "Kỹ sư đã từ chối lời mời cộng tác",
                invitation.farmId(),
                invitation.id(),
                null);
        return toResponse(rejected);
    }

    public List<AuthorizedFarmResponse> listAuthorizedFarms(RequestActor actor) {
        requireAgronomist(actor);
        return authorizationRepository.findByAgronomistIdAndStatus(actor.userId(), AuthorizationStatus.ACTIVE).stream()
                .map(authorization -> expireAuthorizationIfNeeded(actor, authorization))
                .filter(authorization -> authorization.status() == AuthorizationStatus.ACTIVE)
                .sorted(Comparator.comparing(FarmAuthorization::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(this::toAuthorizedFarmResponse)
                .toList();
    }

    private AgronomistInvitation markInvitationAcceptedAtomically(
            RequestActor actor,
            String invitationId,
            Instant now) {
        Query query = new Query(Criteria.where("_id").is(invitationId)
                .and("agronomistId").is(actor.userId())
                .and("status").is(AgronomistInvitationStatus.PENDING)
                .and("expiresAt").gt(now));
        Update update = new Update()
                .set("status", AgronomistInvitationStatus.ACCEPTED)
                .set("blocksNewInvitation", false)
                .set("respondedAt", now);
        AgronomistInvitation accepted = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                AgronomistInvitation.class);
        if (accepted == null) {
            throw new FarmConflictException("Only pending and non-expired invitations can be accepted");
        }
        return accepted;
    }

    private void compensateAcceptedInvitation(AgronomistInvitation accepted) {
        Query query = new Query(Criteria.where("_id").is(accepted.id())
                .and("status").is(AgronomistInvitationStatus.ACCEPTED));
        Update update = new Update()
                .set("status", AgronomistInvitationStatus.PENDING)
                .set("blocksNewInvitation", true)
                .unset("respondedAt");
        mongoTemplate.updateFirst(query, update, AgronomistInvitation.class);
    }

    private void requireAuthenticated(RequestActor actor) {
        if (actor == null || !StringUtils.hasText(actor.userId())) {
            throw new FarmAuthenticationException("Authenticated user is required");
        }
    }

    private void requireAgronomist(RequestActor actor) {
        requireAuthenticated(actor);
        if (!rolePolicy.isAgronomist(actor.role())) {
            throw new FarmAccessDeniedException("Agronomist role is required");
        }
    }

    private Farm requireOwnedFarm(RequestActor actor, String farmId) {
        requireAuthenticated(actor);
        Farm farm = farmRepository.findById(requireText(farmId, "Farm id is required"))
                .orElseThrow(() -> new FarmNotFoundException("Farm was not found"));
        if (!Objects.equals(farm.ownerUserId(), actor.userId())) {
            throw new FarmAccessDeniedException("Only the farm owner can perform this action");
        }
        return farm;
    }

    private AgronomistInvitation requireOwnInvitation(RequestActor actor, String invitationId) {
        AgronomistInvitation invitation = requireInvitation(invitationId);
        if (!Objects.equals(invitation.agronomistId(), actor.userId())) {
            throw new FarmAccessDeniedException("Only the invited agronomist can perform this action");
        }
        return invitation;
    }

    private AgronomistInvitation requireInvitation(String invitationId) {
        return invitationRepository.findById(requireText(invitationId, "Invitation id is required"))
                .orElseThrow(() -> new FarmNotFoundException("Invitation was not found"));
    }

    private FarmAuthorization requireAuthorization(String authorizationId) {
        return authorizationRepository.findById(requireText(authorizationId, "Authorization id is required"))
                .orElseThrow(() -> new FarmNotFoundException("Farm authorization was not found"));
    }

    private AgronomistInvitation expireInvitationIfNeeded(AgronomistInvitation invitation) {
        if (invitation.status() == AgronomistInvitationStatus.PENDING && isExpired(invitation.expiresAt())) {
            AgronomistInvitation expired = invitationRepository.save(copyInvitation(
                    invitation,
                    AgronomistInvitationStatus.EXPIRED,
                    Instant.now()));
            auditService.record(
                    FarmAuthorizationAuditAction.INVITATION_EXPIRED,
                    new RequestActor("system", "", "SYSTEM"),
                    expired.farmId(),
                    expired.agronomistId(),
                    expired.id(),
                    invitationSnapshot(invitation),
                    invitationSnapshot(expired));
            return expired;
        }
        return invitation;
    }

    private FarmAuthorization expireAuthorizationIfNeeded(RequestActor actor, FarmAuthorization authorization) {
        if (authorization.status() == AuthorizationStatus.ACTIVE && isExpired(authorization.expiresAt())) {
            Instant now = Instant.now();
            FarmAuthorization expired = authorizationRepository.save(new FarmAuthorization(
                    authorization.id(),
                    authorization.farmId(),
                    authorization.ownerId(),
                    authorization.agronomistId(),
                    AuthorizationStatus.EXPIRED,
                    false,
                    authorization.permissions(),
                    authorization.allowedCultivationAreaIds(),
                    authorization.grantedAt(),
                    authorization.expiresAt(),
                    null,
                    authorization.createdAt(),
                    now,
                    authorization.version()));
            auditService.record(
                    FarmAuthorizationAuditAction.AUTHORIZATION_EXPIRED,
                    actor,
                    expired.farmId(),
                    expired.agronomistId(),
                    expired.id(),
                    authorizationSnapshot(authorization),
                    authorizationSnapshot(expired));
            return expired;
        }
        return authorization;
    }

    private List<FarmPermissionType> normalizePermissions(List<FarmPermissionType> permissions) {
        List<FarmPermissionType> source = permissions == null || permissions.isEmpty()
                ? new ArrayList<>(DEFAULT_PERMISSIONS)
                : permissions;
        return new ArrayList<>(new LinkedHashSet<>(source));
    }

    private List<String> normalizeAreaScope(Farm farm, List<String> requestedAreaIds) {
        List<String> existingAreaIds = farm.zones() == null
                ? List.of()
                : farm.zones().stream().map(FarmZone::id).filter(StringUtils::hasText).toList();
        if (requestedAreaIds == null || requestedAreaIds.isEmpty()) {
            return existingAreaIds;
        }
        List<String> normalized = requestedAreaIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (!existingAreaIds.containsAll(normalized)) {
            throw new FarmInvalidRequestException("Allowed cultivation areas must belong to the farm");
        }
        return normalized;
    }

    private boolean isExpired(Instant expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new FarmInvalidRequestException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AgronomistInvitation copyInvitation(
            AgronomistInvitation invitation,
            AgronomistInvitationStatus status,
            Instant respondedAt) {
        return new AgronomistInvitation(
                invitation.id(),
                invitation.farmId(),
                invitation.ownerId(),
                invitation.agronomistId(),
                status,
                status == AgronomistInvitationStatus.PENDING,
                invitation.message(),
                invitation.initialPermissions(),
                invitation.initialAllowedCultivationAreaIds(),
                invitation.createdAt(),
                respondedAt,
                invitation.expiresAt());
    }

    private Map<String, Object> invitationSnapshot(AgronomistInvitation invitation) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", invitation.status().name());
        snapshot.put("permissions", invitation.initialPermissions());
        snapshot.put("allowedCultivationAreaIds", invitation.initialAllowedCultivationAreaIds());
        snapshot.put("expiresAt", invitation.expiresAt());
        return snapshot;
    }

    private Map<String, Object> authorizationSnapshot(FarmAuthorization authorization) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", authorization.status().name());
        snapshot.put("permissions", authorization.permissions());
        snapshot.put("allowedCultivationAreaIds", authorization.allowedCultivationAreaIds());
        snapshot.put("expiresAt", authorization.expiresAt());
        return snapshot;
    }

    private Map<String, Object> rejectionSnapshot(
            AgronomistInvitation invitation,
            RejectAgronomistInvitationRequest request) {
        Map<String, Object> snapshot = invitationSnapshot(invitation);
        snapshot.put("reason", normalizeOptionalText(request.reason()));
        return snapshot;
    }

    private void publish(
            String eventType,
            String receiverId,
            String title,
            String message,
            String farmId,
            String invitationId,
            String authorizationId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("farmId", farmId);
        metadata.put("invitationId", invitationId);
        metadata.put("authorizationId", authorizationId);
        notificationPublisher.publish(
                eventType,
                receiverId,
                title,
                message,
                metadata);
    }

    private AgronomistInvitationResponse toResponse(AgronomistInvitation invitation) {
        return new AgronomistInvitationResponse(
                invitation.id(),
                invitation.farmId(),
                invitation.ownerId(),
                invitation.agronomistId(),
                invitation.status(),
                invitation.message(),
                invitation.initialPermissions(),
                invitation.initialAllowedCultivationAreaIds(),
                invitation.createdAt(),
                invitation.respondedAt(),
                invitation.expiresAt());
    }

    private FarmAuthorizationResponse toResponse(FarmAuthorization authorization) {
        return new FarmAuthorizationResponse(
                authorization.id(),
                authorization.farmId(),
                authorization.ownerId(),
                authorization.agronomistId(),
                authorization.status(),
                authorization.permissions(),
                authorization.allowedCultivationAreaIds(),
                authorization.grantedAt(),
                authorization.expiresAt(),
                authorization.revokedAt(),
                authorization.createdAt(),
                authorization.updatedAt(),
                authorization.version());
    }

    private AuthorizedFarmResponse toAuthorizedFarmResponse(FarmAuthorization authorization) {
        Farm farm = farmRepository.findById(authorization.farmId()).orElse(null);
        return new AuthorizedFarmResponse(
                authorization.id(),
                authorization.farmId(),
                farm == null ? null : farm.name(),
                authorization.ownerId(),
                authorization.status(),
                authorization.permissions(),
                authorization.allowedCultivationAreaIds(),
                authorization.grantedAt(),
                authorization.expiresAt());
    }
}
