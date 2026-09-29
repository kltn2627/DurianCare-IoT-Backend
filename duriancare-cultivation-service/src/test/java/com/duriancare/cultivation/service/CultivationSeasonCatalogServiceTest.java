package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.cultivation.api.CultivationCalendarDtos;
import com.duriancare.cultivation.domain.CultivationSeason;
import com.duriancare.cultivation.repository.CultivationSeasonRepository;
import com.duriancare.cultivation.security.CultivationAccessDeniedException;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import com.duriancare.cultivation.security.CultivationActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CultivationSeasonCatalogServiceTest {

    @Mock
    private CultivationSeasonRepository seasonRepository;
    @Mock
    private CultivationAccessGuard accessGuard;

    private CultivationSeasonCatalogService service;

    @BeforeEach
    void setUp() {
        service = new CultivationSeasonCatalogService(seasonRepository, accessGuard);
    }

    @Test
    void farmerCreatesSeasonUnderAccessibleFarmArea() {
        var request = new CultivationCalendarDtos.CreateCultivationSeasonRequest(
                "farm-1",
                "zone-1",
                "Vu mua 2026",
                "DURIAN",
                "Ri6",
                LocalDate.parse("2026-08-01"),
                null,
                "owner-1");
        when(seasonRepository.save(org.mockito.ArgumentMatchers.any(CultivationSeason.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CultivationSeason season = service.createSeason(owner(), request);

        assertThat(season.farmId()).isEqualTo("farm-1");
        assertThat(season.plotId()).isEqualTo("zone-1");
        assertThat(season.name()).isEqualTo("Vu mua 2026");
        verify(accessGuard).requireCreate(owner(), "CultivationSeason", null, "farm-1", "zone-1");
    }

    @Test
    void seasonCreateDeniesForeignArea() {
        var request = new CultivationCalendarDtos.CreateCultivationSeasonRequest(
                "farm-2",
                "zone-9",
                "Foreign",
                "DURIAN",
                null,
                LocalDate.parse("2026-08-01"),
                null,
                "owner-1");
        doThrow(new CultivationAccessDeniedException("Access denied for cultivation resource"))
                .when(accessGuard).requireCreate(owner(), "CultivationSeason", null, "farm-2", "zone-9");

        assertThatThrownBy(() -> service.createSeason(owner(), request))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    @Test
    void listSeasonsChecksFarmScope() {
        when(seasonRepository.findByFarmIdAndPlotIdOrderByStartDateDesc("farm-1", "zone-1"))
                .thenReturn(List.of(season()));

        List<CultivationSeason> seasons = service.listSeasons(owner(), "farm-1", "zone-1");

        assertThat(seasons).hasSize(1);
        verify(accessGuard).requireView(owner(), "CultivationSeason", null, "farm-1", "zone-1");
    }

    private CultivationActor owner() {
        return new CultivationActor("owner-1", "owner@example.com", "FARMER");
    }

    private CultivationSeason season() {
        Instant now = Instant.now();
        return new CultivationSeason(
                "season-1",
                "farm-1",
                "zone-1",
                "Vu mua 2026",
                "DURIAN",
                "Ri6",
                LocalDate.parse("2026-08-01"),
                null,
                "owner-1",
                now,
                now);
    }
}
