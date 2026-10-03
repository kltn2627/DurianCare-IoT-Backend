package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmStatus;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.ZoneStatus;
import com.duriancare.farm.dto.FarmCatalogDtos;
import com.duriancare.farm.dto.RequestActor;
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
class FarmCatalogServiceTest {

    @Mock
    private FarmRepository farmRepository;

    private FarmCatalogService service;

    @BeforeEach
    void setUp() {
        service = new FarmCatalogService(farmRepository, new AgronomistRolePolicy());
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
