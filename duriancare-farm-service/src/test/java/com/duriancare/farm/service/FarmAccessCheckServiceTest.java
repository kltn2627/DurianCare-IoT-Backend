package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmAuthorization;
import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.ZoneStatus;
import com.duriancare.farm.dto.FarmAccessCheckRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FarmAccessCheckServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private FarmAuthorizationRepository authorizationRepository;

    private FarmAccessCheckService service;

    @BeforeEach
    void setUp() {
        service = new FarmAccessCheckService(farmRepository, authorizationRepository, new AgronomistRolePolicy());
    }

    @Test
    void ownerAccessesOwnFarm() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));

        assertThat(service.canAccess(request("owner-1", "FARMER", "farm-1", "zone-1"))).isTrue();
    }

    @Test
    void ownerCannotAccessAnotherFarm() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-2")));

        assertThat(service.canAccess(request("owner-1", "FARMER", "farm-1", "zone-1"))).isFalse();
    }

    @Test
    void authorizedEngineerAccessesAllowedFarm() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndAgronomistIdAndStatus(
                "farm-1", "engineer-1", AuthorizationStatus.ACTIVE))
                .thenReturn(Optional.of(authorization(AuthorizationStatus.ACTIVE, List.of("zone-1"), Instant.now().plusSeconds(3600))));

        assertThat(service.canAccess(request("engineer-1", "ENGINEER", "farm-1", "zone-1"))).isTrue();
    }

    @Test
    void engineerWithoutAuthorizationIsDenied() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndAgronomistIdAndStatus(
                "farm-1", "engineer-1", AuthorizationStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.canAccess(request("engineer-1", "ENGINEER", "farm-1", "zone-1"))).isFalse();
    }

    @Test
    void revokedAuthorizationIsDenied() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndAgronomistIdAndStatus(
                "farm-1", "engineer-1", AuthorizationStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.canAccess(request("engineer-1", "ENGINEER", "farm-1", "zone-1"))).isFalse();
    }

    @Test
    void expiredAuthorizationIsDenied() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndAgronomistIdAndStatus(
                "farm-1", "engineer-1", AuthorizationStatus.ACTIVE))
                .thenReturn(Optional.of(authorization(AuthorizationStatus.ACTIVE, List.of("zone-1"), Instant.now().minusSeconds(10))));

        assertThat(service.canAccess(request("engineer-1", "ENGINEER", "farm-1", "zone-1"))).isFalse();
    }

    @Test
    void areaScopedEngineerAccessesAllowedArea() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndAgronomistIdAndStatus(
                "farm-1", "engineer-1", AuthorizationStatus.ACTIVE))
                .thenReturn(Optional.of(authorization(AuthorizationStatus.ACTIVE, List.of("zone-1"), null)));

        assertThat(service.canAccess(request("engineer-1", "EXPERT", "farm-1", "zone-1"))).isTrue();
    }

    @Test
    void areaScopedEngineerCannotAccessOtherArea() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndAgronomistIdAndStatus(
                "farm-1", "engineer-1", AuthorizationStatus.ACTIVE))
                .thenReturn(Optional.of(authorization(AuthorizationStatus.ACTIVE, List.of("zone-1"), null)));

        assertThat(service.canAccess(request("engineer-1", "ENGINEER", "farm-1", "zone-2"))).isFalse();
    }

    @Test
    void irrelevantRoleIsDenied() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));

        assertThat(service.canAccess(request("guest-1", "GUEST", "farm-1", "zone-1"))).isFalse();
    }

    @Test
    void anonymousRequestReturnsAuthenticationError() {
        assertThatThrownBy(() -> service.canAccess(new FarmAccessCheckRequest("", "", "farm-1", "zone-1", FarmPermissionType.VIEW_CARE_SCHEDULE)))
                .isInstanceOf(FarmAuthenticationException.class);
    }

    @Test
    void alertRecipientsIncludeOwnerAndActiveScopedEngineerWithPermission() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndStatus("farm-1", AuthorizationStatus.ACTIVE))
                .thenReturn(List.of(
                        authorization("engineer-1", AuthorizationStatus.ACTIVE, List.of("zone-1"), null, List.of(FarmPermissionType.READ_IOT)),
                        authorization("engineer-2", AuthorizationStatus.ACTIVE, List.of("zone-2"), null, List.of(FarmPermissionType.READ_IOT)),
                        authorization("engineer-3", AuthorizationStatus.ACTIVE, List.of("zone-1"), null, List.of(FarmPermissionType.VIEW_FARM)),
                        authorization("engineer-4", AuthorizationStatus.ACTIVE, List.of("zone-1"), Instant.now().minusSeconds(60), List.of(FarmPermissionType.READ_IOT))));

        assertThat(service.alertRecipients("farm-1", "zone-1", FarmPermissionType.READ_IOT))
                .containsExactly("owner-1", "engineer-1");
    }

    @Test
    void alertRecipientLookupQueriesOnlyActiveAuthorizations() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-1")));
        when(authorizationRepository.findByFarmIdAndStatus("farm-1", AuthorizationStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.alertRecipients("farm-1", "zone-1", FarmPermissionType.READ_IOT))
                .containsExactly("owner-1");
    }

    private FarmAccessCheckRequest request(String userId, String role, String farmId, String areaId) {
        return new FarmAccessCheckRequest(userId, role, farmId, areaId, FarmPermissionType.VIEW_CARE_SCHEDULE);
    }

    private Farm farm(String farmId, String ownerId) {
        return new Farm(
                farmId,
                ownerId,
                "Farm",
                "Address",
                "Province",
                "District",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                FarmStatus.ACTIVE,
                List.of(zone("zone-1"), zone("zone-2")),
                Instant.now(),
                Instant.now());
    }

    private FarmZone zone(String id) {
        return new FarmZone(id, id, id, BigDecimal.ONE, Map.of(), null, ZoneStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private FarmAuthorization authorization(AuthorizationStatus status, List<String> areas, Instant expiresAt) {
        return authorization("engineer-1", status, areas, expiresAt, List.of(FarmPermissionType.VIEW_CARE_SCHEDULE, FarmPermissionType.CREATE_CARE_SCHEDULE, FarmPermissionType.UPDATE_CARE_SCHEDULE));
    }

    private FarmAuthorization authorization(String engineerId, AuthorizationStatus status, List<String> areas, Instant expiresAt, List<FarmPermissionType> permissions) {
        return new FarmAuthorization(
                "auth-1",
                "farm-1",
                "owner-1",
                engineerId,
                status,
                true,
                permissions,
                areas,
                Instant.now().minusSeconds(3600),
                expiresAt,
                status == AuthorizationStatus.REVOKED ? Instant.now() : null,
                Instant.now(),
                Instant.now(),
                null);
    }
}
