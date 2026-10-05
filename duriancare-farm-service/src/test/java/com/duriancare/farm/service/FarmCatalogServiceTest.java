package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.FarmAuthorization;
import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.domain.ZoneStatus;
import com.duriancare.farm.dto.FarmCatalogDtos;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.repository.FarmAuthorizationRepository;
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
class FarmCatalogServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private FarmAuthorizationRepository authorizationRepository;

    private FarmCatalogService service;

    @BeforeEach
    void setUp() {
        service = new FarmCatalogService(farmRepository, authorizationRepository, new AgronomistRolePolicy());
    }

    @Test
    void farmerListsOnlyOwnActiveFarms() {
        Farm farm = farm("farm-1", "owner-1");
        when(farmRepository.findByOwnerUserIdAndStatusNot("owner-1", FarmStatus.ARCHIVED)).thenReturn(List.of(farm));

        var farms = service.listOwnedFarms(owner());

        assertThat(farms).hasSize(1);
        assertThat(farms.getFirst().id()).isEqualTo("farm-1");
        assertThat(farms.getFirst().zones()).hasSize(1);
    }

    @Test
    void farmerCannotReadForeignFarmAsOwned() {
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm("farm-1", "owner-2")));

        assertThatThrownBy(() -> service.getOwnedFarm(owner(), "farm-1"))
                .isInstanceOf(FarmAccessDeniedException.class);
    }

    @Test
    void farmerCreatesZoneUnderOwnFarm() {
        Farm farm = farm("farm-1", "owner-1");
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(farm));
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var zone = service.createZone(owner(), "farm-1", new FarmCatalogDtos.CreateFarmZoneRequest(
                "Khu A1",
                "A1",
                BigDecimal.TEN,
                Map.of(),
                "Khu dau tien",
                null,
                null));

        assertThat(zone.id()).isNotBlank();
        assertThat(zone.name()).isEqualTo("Khu A1");
        assertThat(zone.status()).isEqualTo(ZoneStatus.ACTIVE);
    }

    @Test
    void engineerCannotCreateOwnerFarm() {
        assertThatThrownBy(() -> service.createFarm(
                new RequestActor("engineer-1", "eng@example.com", "ENGINEER"),
                new FarmCatalogDtos.CreateFarmRequest("Farm", null, null, null, null, null, null)))
                .isInstanceOf(FarmAccessDeniedException.class);
    }

    @Test
    void engineerCalendarListsOnlyAuthorizedZonesById() {
        var grant = authorization(List.of(FarmPermissionType.VIEW_CARE_SCHEDULE),
                Instant.now().plusSeconds(3600), List.of("zone-1"));
        var original = farm("farm-1", "owner-1");
        var secondZone = new FarmZone("zone-2", "Zone 2", "Z2", BigDecimal.ONE, Map.of(),
                null, ZoneStatus.ACTIVE, Instant.now(), Instant.now());
        var twoZoneFarm = new Farm(original.id(), original.ownerUserId(), original.name(), original.address(),
                original.province(), original.district(), original.latitude(), original.longitude(),
                original.areaHectares(), original.status(), List.of(original.zones().getFirst(), secondZone),
                original.createdAt(), original.updatedAt());
        when(authorizationRepository.findByAgronomistIdAndStatus("engineer-1", AuthorizationStatus.ACTIVE))
                .thenReturn(List.of(grant));
        when(farmRepository.findById("farm-1")).thenReturn(Optional.of(twoZoneFarm));

        var visible = service.listCalendarFarms(new RequestActor("engineer-1", "e@example.com", "ENGINEER"));

        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().zones()).extracting(FarmCatalogDtos.FarmZoneResponse::id)
                .containsExactly("zone-1");
    }

    @Test
    void expiredEngineerGrantDoesNotExposeFarmCalendar() {
        when(authorizationRepository.findByAgronomistIdAndStatus("engineer-1", AuthorizationStatus.ACTIVE))
                .thenReturn(List.of(authorization(List.of(FarmPermissionType.VIEW_CARE_SCHEDULE),
                        Instant.now().minusSeconds(1), List.of("zone-1"))));
        assertThat(service.listCalendarFarms(new RequestActor("engineer-1", "e@example.com", "ENGINEER")))
                .isEmpty();
    }

    @Test
    void administratorCalendarUsesPersistedFarmsWithoutArchivedEntries() {
        when(farmRepository.findAll()).thenReturn(List.of(farm("farm-1", "owner-1")));
        assertThat(service.listCalendarFarms(new RequestActor("admin-1", "a@example.com", "ADMIN")))
                .extracting(FarmCatalogDtos.FarmResponse::id).containsExactly("farm-1");
    }

    private FarmAuthorization authorization(List<FarmPermissionType> permissions, Instant validUntil,
            List<String> zones) {
        return new FarmAuthorization("auth-1", "farm-1", "owner-1", "engineer-1",
                AuthorizationStatus.ACTIVE, false, permissions, zones, Instant.now(), validUntil,
                null, Instant.now(), Instant.now(), null);
    }

    private RequestActor owner() {
        return new RequestActor("owner-1", "owner@example.com", "FARMER");
    }

    private Farm farm(String id, String ownerId) {
        Instant now = Instant.now();
        return new Farm(
                id,
                ownerId,
                "Farm",
                "Address",
                "Province",
                "District",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                FarmStatus.ACTIVE,
                List.of(new FarmZone("zone-1", "Zone 1", "Z1", BigDecimal.ONE, Map.of(), null, ZoneStatus.ACTIVE, null, null, now, now)),
                now,
                now);
    }
}
