package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.AgronomistInvitation;
import com.duriancare.farm.domain.AgronomistInvitationStatus;
import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmAuthorization;
import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.ZoneStatus;
import com.duriancare.farm.dto.AgronomistSummaryResponse;
import com.duriancare.farm.dto.CreateAgronomistInvitationRequest;
import com.duriancare.farm.dto.RejectAgronomistInvitationRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.UpdateFarmAuthorizationRequest;
import com.duriancare.farm.event.publisher.FarmNotificationEventPublisher;
import com.duriancare.farm.repository.AgronomistInvitationRepository;
import com.duriancare.farm.repository.FarmAuthorizationRepository;
import com.duriancare.farm.repository.FarmRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class AgronomistAuthorizationServiceTest {

    private static final RequestActor OWNER = new RequestActor("owner-1", "owner@example.com", "FARMER");
    private static final RequestActor OTHER_OWNER = new RequestActor("owner-2", "other@example.com", "FARMER");
    private static final RequestActor AGRONOMIST = new RequestActor("agronomist-1", "agro@example.com", "ENGINEER");

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private AgronomistInvitationRepository invitationRepository;
    @Mock
    private FarmAuthorizationRepository authorizationRepository;
    @Mock
    private AgronomistDirectoryClient agronomistDirectoryClient;
    @Mock
    private FarmAuthorizationAuditService auditService;
    @Mock
    private FarmNotificationEventPublisher notificationPublisher;
    @Mock
    private MongoTemplate mongoTemplate;

    private AgronomistAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AgronomistAuthorizationService(
                farmRepository,
                invitationRepository,
                authorizationRepository,
                agronomistDirectoryClient,
                auditService,
                notificationPublisher,
                new AgronomistRolePolicy(),
                mongoTemplate);
    }

    @Test
    void ownerCreatesInvitationWithDefaultScopeAndPermissions() {
        Farm farm = farm();
        when(farmRepository.findById(farm.id())).thenReturn(Optional.of(farm));
        when(agronomistDirectoryClient.getEligible(AGRONOMIST.userId())).thenReturn(agronomist());
        when(invitationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                farm.id(), AGRONOMIST.userId(), AgronomistInvitationStatus.PENDING)).thenReturn(false);
        when(authorizationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                farm.id(), AGRONOMIST.userId(), AuthorizationStatus.ACTIVE)).thenReturn(false);
        when(invitationRepository.save(any())).thenAnswer(invocation -> {
            AgronomistInvitation invitation = invocation.getArgument(0);
            return new AgronomistInvitation(
                    "invite-1",
                    invitation.farmId(),
                    invitation.ownerId(),
                    invitation.agronomistId(),
                    invitation.status(),
                    invitation.blocksNewInvitation(),
                    invitation.message(),
                    invitation.initialPermissions(),
                    invitation.initialAllowedCultivationAreaIds(),
                    invitation.createdAt(),
                    invitation.respondedAt(),
                    invitation.expiresAt());
        });

        var response = service.createInvitation(
                OWNER,
                farm.id(),
                new CreateAgronomistInvitationRequest(AGRONOMIST.userId(), "Moi ho tro", null, null, null));

        assertThat(response.id()).isEqualTo("invite-1");
        assertThat(response.status()).isEqualTo(AgronomistInvitationStatus.PENDING);
        assertThat(response.initialAllowedCultivationAreaIds()).containsExactly("zone-1", "zone-2");
        assertThat(response.initialPermissions()).contains(FarmPermissionType.VIEW_FARM, FarmPermissionType.CHAT_WITH_OWNER);
        verify(notificationPublisher).publish(
                eq("AGRONOMIST_INVITATION_CREATED"),
                eq(AGRONOMIST.userId()),
                any(),
                any(),
                any(Map.class));
    }

    @Test
    void nonOwnerCannotCreateInvitation() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm()));

        assertThatThrownBy(() -> service.createInvitation(
                OTHER_OWNER,
                "farm-1",
                new CreateAgronomistInvitationRequest(AGRONOMIST.userId(), null, null, null, null)))
                .isInstanceOf(FarmAccessDeniedException.class);
    }

    @Test
    void duplicatePendingInvitationIsRejected() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm()));
        when(agronomistDirectoryClient.getEligible(AGRONOMIST.userId())).thenReturn(agronomist());
        when(invitationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                "farm-1", AGRONOMIST.userId(), AgronomistInvitationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.createInvitation(
                OWNER,
                "farm-1",
                new CreateAgronomistInvitationRequest(AGRONOMIST.userId(), null, null, null, null)))
                .isInstanceOf(FarmConflictException.class);
    }

    @Test
    void duplicateKeyDuringInvitationCreateReturnsConflict() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm()));
        when(agronomistDirectoryClient.getEligible(AGRONOMIST.userId())).thenReturn(agronomist());
        when(invitationRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate pending invitation"));

        assertThatThrownBy(() -> service.createInvitation(
                OWNER,
                "farm-1",
                new CreateAgronomistInvitationRequest(AGRONOMIST.userId(), null, null, null, null)))
                .isInstanceOf(FarmConflictException.class);
    }

    @Test
    void invalidAgronomistCannotBeInvited() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm()));
        when(agronomistDirectoryClient.getEligible(AGRONOMIST.userId()))
                .thenThrow(new FarmInvalidRequestException("Agronomist is not eligible for invitation"));

        assertThatThrownBy(() -> service.createInvitation(
                OWNER,
                "farm-1",
                new CreateAgronomistInvitationRequest(AGRONOMIST.userId(), null, null, null, null)))
                .isInstanceOf(FarmInvalidRequestException.class);
    }

    @Test
    void activeAuthorizationBlocksNewInvitation() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm()));
        when(agronomistDirectoryClient.getEligible(AGRONOMIST.userId())).thenReturn(agronomist());
        when(authorizationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                "farm-1", AGRONOMIST.userId(), AuthorizationStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.createInvitation(
                OWNER,
                "farm-1",
                new CreateAgronomistInvitationRequest(AGRONOMIST.userId(), null, null, null, null)))
                .isInstanceOf(FarmConflictException.class);
    }

    @Test
    void invitationCannotUseAreaOutsideFarm() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm()));
        when(agronomistDirectoryClient.getEligible(AGRONOMIST.userId())).thenReturn(agronomist());

        assertThatThrownBy(() -> service.createInvitation(
                OWNER,
                "farm-1",
                new CreateAgronomistInvitationRequest(
                        AGRONOMIST.userId(),
                        null,
                        null,
                        List.of("zone-x"),
                        null)))
                .isInstanceOf(FarmInvalidRequestException.class);
    }

    @Test
    void agronomistAcceptsInvitationAndAuthorizationIsCreated() {
        AgronomistInvitation invitation = pendingInvitation(Instant.now().plusSeconds(3600));
        when(invitationRepository.findById(invitation.id())).thenReturn(Optional.of(invitation));
        when(authorizationRepository.existsByFarmIdAndAgronomistIdAndStatus(
                invitation.farmId(), invitation.agronomistId(), AuthorizationStatus.ACTIVE)).thenReturn(false);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(AgronomistInvitation.class))).thenReturn(new AgronomistInvitation(
                        invitation.id(),
                        invitation.farmId(),
                        invitation.ownerId(),
                        invitation.agronomistId(),
                        AgronomistInvitationStatus.ACCEPTED,
                        false,
                        invitation.message(),
                        invitation.initialPermissions(),
                        invitation.initialAllowedCultivationAreaIds(),
                        invitation.createdAt(),
                        Instant.now(),
                        invitation.expiresAt()));
        when(authorizationRepository.save(any())).thenAnswer(invocation -> {
            FarmAuthorization authorization = invocation.getArgument(0);
            return new FarmAuthorization(
                    "auth-1",
                    authorization.farmId(),
                    authorization.ownerId(),
                    authorization.agronomistId(),
                    authorization.status(),
                    authorization.blocksNewAuthorization(),
                    authorization.permissions(),
                    authorization.allowedCultivationAreaIds(),
                    authorization.grantedAt(),
                    authorization.expiresAt(),
                    authorization.revokedAt(),
                    authorization.createdAt(),
                    authorization.updatedAt(),
                    0L);
        });

        var response = service.acceptInvitation(AGRONOMIST, invitation.id());

        assertThat(response.id()).isEqualTo("auth-1");
        assertThat(response.status()).isEqualTo(AuthorizationStatus.ACTIVE);
        ArgumentCaptor<FarmAuthorization> captor = ArgumentCaptor.forClass(FarmAuthorization.class);
        verify(authorizationRepository).save(captor.capture());
        assertThat(captor.getValue().permissions()).containsExactly(FarmPermissionType.VIEW_FARM);
    }

    @Test
    void concurrentAcceptLoserDoesNotCreateAuthorization() {
        AgronomistInvitation invitation = pendingInvitation(Instant.now().plusSeconds(3600));
        when(invitationRepository.findById(invitation.id())).thenReturn(Optional.of(invitation));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(AgronomistInvitation.class))).thenReturn(null);

        assertThatThrownBy(() -> service.acceptInvitation(AGRONOMIST, invitation.id()))
                .isInstanceOf(FarmConflictException.class);
        verify(authorizationRepository, never()).save(any());
    }

    @Test
    void duplicateActiveAuthorizationDuringAcceptIsCompensated() {
        AgronomistInvitation invitation = pendingInvitation(Instant.now().plusSeconds(3600));
        AgronomistInvitation accepted = new AgronomistInvitation(
                invitation.id(),
                invitation.farmId(),
                invitation.ownerId(),
                invitation.agronomistId(),
                AgronomistInvitationStatus.ACCEPTED,
                false,
                invitation.message(),
                invitation.initialPermissions(),
                invitation.initialAllowedCultivationAreaIds(),
                invitation.createdAt(),
                Instant.now(),
                invitation.expiresAt());
        when(invitationRepository.findById(invitation.id())).thenReturn(Optional.of(invitation));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(AgronomistInvitation.class))).thenReturn(accepted);
        when(authorizationRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate active auth"));

        assertThatThrownBy(() -> service.acceptInvitation(AGRONOMIST, invitation.id()))
                .isInstanceOf(FarmConflictException.class);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(AgronomistInvitation.class));
    }

    @Test
    void wrongAgronomistCannotAcceptInvitation() {
        when(invitationRepository.findById("invite-1")).thenReturn(Optional.of(pendingInvitation(Instant.now().plusSeconds(3600))));

        assertThatThrownBy(() -> service.acceptInvitation(
                new RequestActor("agronomist-2", "agro2@example.com", "ENGINEER"),
                "invite-1"))
                .isInstanceOf(FarmAccessDeniedException.class);
    }

    @Test
    void expiredInvitationCannotBeAccepted() {
        AgronomistInvitation invitation = pendingInvitation(Instant.now().minusSeconds(1));
        when(invitationRepository.findById(invitation.id())).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.acceptInvitation(AGRONOMIST, invitation.id()))
                .isInstanceOf(FarmConflictException.class);
    }

    @Test
    void cancelledInvitationCannotBeAccepted() {
        AgronomistInvitation invitation = new AgronomistInvitation(
                "invite-1",
                "farm-1",
                OWNER.userId(),
                AGRONOMIST.userId(),
                AgronomistInvitationStatus.CANCELLED,
                false,
                null,
                List.of(FarmPermissionType.VIEW_FARM),
                List.of("zone-1"),
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(3600));
        when(invitationRepository.findById(invitation.id())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitation(AGRONOMIST, invitation.id()))
                .isInstanceOf(FarmConflictException.class);
    }

    @Test
    void ownerUpdatesActiveAuthorization() {
        FarmAuthorization authorization = activeAuthorization();
        when(authorizationRepository.findById(authorization.id())).thenReturn(Optional.of(authorization));
        when(farmRepository.findById(authorization.farmId())).thenReturn(Optional.of(farm()));
        when(authorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateAuthorization(
                OWNER,
                authorization.id(),
                new UpdateFarmAuthorizationRequest(
                        List.of(FarmPermissionType.VIEW_FARM, FarmPermissionType.VIEW_SENSOR_DATA),
                        List.of("zone-1"),
                        null));

        assertThat(response.permissions()).containsExactly(FarmPermissionType.VIEW_FARM, FarmPermissionType.VIEW_SENSOR_DATA);
        assertThat(response.allowedCultivationAreaIds()).containsExactly("zone-1");
    }

    @Test
    void nonOwnerCannotUpdateAuthorization() {
        FarmAuthorization authorization = activeAuthorization();
        when(authorizationRepository.findById(authorization.id())).thenReturn(Optional.of(authorization));
        when(farmRepository.findById(authorization.farmId())).thenReturn(Optional.of(farm()));

        assertThatThrownBy(() -> service.updateAuthorization(
                OTHER_OWNER,
                authorization.id(),
                new UpdateFarmAuthorizationRequest(List.of(FarmPermissionType.VIEW_FARM), List.of("zone-1"), null)))
                .isInstanceOf(FarmAccessDeniedException.class);
    }

    @Test
    void nonOwnerCannotRevokeAuthorization() {
        FarmAuthorization authorization = activeAuthorization();
        when(authorizationRepository.findById(authorization.id())).thenReturn(Optional.of(authorization));
        when(farmRepository.findById(authorization.farmId())).thenReturn(Optional.of(farm()));

        assertThatThrownBy(() -> service.revokeAuthorization(OTHER_OWNER, authorization.id()))
                .isInstanceOf(FarmAccessDeniedException.class);
    }

    @Test
    void ownerRevokesAuthorizationWithoutDeletingRecord() {
        FarmAuthorization authorization = activeAuthorization();
        when(authorizationRepository.findById(authorization.id())).thenReturn(Optional.of(authorization));
        when(farmRepository.findById(authorization.farmId())).thenReturn(Optional.of(farm()));
        when(authorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.revokeAuthorization(OWNER, authorization.id());

        ArgumentCaptor<FarmAuthorization> captor = ArgumentCaptor.forClass(FarmAuthorization.class);
        verify(authorizationRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AuthorizationStatus.REVOKED);
        assertThat(captor.getValue().id()).isEqualTo(authorization.id());
    }

    @Test
    void rejectInvitationDoesNotCreateAuthorization() {
        AgronomistInvitation invitation = pendingInvitation(Instant.now().plusSeconds(3600));
        when(invitationRepository.findById(invitation.id())).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.rejectInvitation(
                AGRONOMIST,
                invitation.id(),
                new RejectAgronomistInvitationRequest("Ban lich"));

        assertThat(response.status()).isEqualTo(AgronomistInvitationStatus.REJECTED);
    }

    private Farm farm() {
        return new Farm(
                "farm-1",
                OWNER.userId(),
                "Vuon sau rieng",
                "Xa A",
                "Dak Lak",
                "Huyen B",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                FarmStatus.ACTIVE,
                List.of(zone("zone-1"), zone("zone-2")),
                Instant.now(),
                Instant.now());
    }

    private FarmZone zone(String id) {
        return new FarmZone(
                id,
                "Khu " + id,
                id,
                BigDecimal.TEN,
                Map.of(),
                null,
                ZoneStatus.ACTIVE,
                Instant.now(),
                Instant.now());
    }

    private AgronomistSummaryResponse agronomist() {
        return new AgronomistSummaryResponse(
                AGRONOMIST.userId(),
                "Nguyen Ky Su",
                AGRONOMIST.email(),
                AGRONOMIST.role(),
                "DurianCare",
                "Sau rieng",
                5,
                "Dak Lak",
                null,
                true);
    }

    private AgronomistInvitation pendingInvitation(Instant expiresAt) {
        return new AgronomistInvitation(
                "invite-1",
                "farm-1",
                OWNER.userId(),
                AGRONOMIST.userId(),
                AgronomistInvitationStatus.PENDING,
                true,
                null,
                List.of(FarmPermissionType.VIEW_FARM),
                List.of("zone-1"),
                Instant.now(),
                null,
                expiresAt);
    }

    private FarmAuthorization activeAuthorization() {
        return new FarmAuthorization(
                "auth-1",
                "farm-1",
                OWNER.userId(),
                AGRONOMIST.userId(),
                AuthorizationStatus.ACTIVE,
                true,
                List.of(FarmPermissionType.VIEW_FARM),
                List.of("zone-1", "zone-2"),
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                0L);
    }
}
