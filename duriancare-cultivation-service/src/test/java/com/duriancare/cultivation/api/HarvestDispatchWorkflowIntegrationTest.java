package com.duriancare.cultivation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.security.*;
import com.duriancare.cultivation.service.HarvestDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Fixture integration at the trusted Gateway identity boundary, not a live JWT/Gateway E2E. */
class HarvestDispatchWorkflowIntegrationTest {
    final HarvestBatchRepository batches = mock(HarvestBatchRepository.class);
    final CultivationSeasonRepository seasons = mock(CultivationSeasonRepository.class);
    final ExportReleaseRepository releases = mock(ExportReleaseRepository.class);
    final HarvestDispatchRepository repository = mock(HarvestDispatchRepository.class);
    final FarmAccessClient access = mock(FarmAccessClient.class);
    final Map<String, HarvestDispatch> stored = new HashMap<>();
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    MockMvc mvc;
    HarvestBatch lot;
    Map<String, Object> evidence;
    static final String BASE = "/api/v1/harvest-batches";

    @BeforeEach void setup() {
        Instant harvest = Instant.parse("2025-10-01T00:00:00Z");
        lot = new HarvestBatch("fixture-lot", "FIXTURE-ONLY", "fixture-season", "fixture-farm", "fixture-zone",
                harvest, new BigDecimal("100"), "KG", "VN", null, RiskLevel.LOW, HarvestBatchStatus.HARVESTED, "owner", harvest);
        when(batches.findById("fixture-lot")).thenReturn(Optional.of(lot));
        when(seasons.findById("fixture-season")).thenReturn(Optional.of(new CultivationSeason("fixture-season", "fixture-farm",
                "fixture-zone", "fixture", null, null, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), "owner", harvest, harvest)));
        when(releases.findById("fixture-release")).thenReturn(Optional.of(new ExportRelease("fixture-release", "FIXTURE",
                "fixture-lot", "VN", Map.of(), Map.of(), ExportReleaseStatus.RELEASED, "owner", harvest, null, null, null, harvest, harvest)));
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(repository.findByFarmId(anyString())).thenAnswer(i -> stored.values().stream()
                .filter(r -> r.farmId().equals(i.getArgument(0))).toList());
        when(repository.insert(any(HarvestDispatch.class))).thenAnswer(i -> {
            HarvestDispatch record = i.getArgument(0);
            stored.put(record.id(), record);
            return record;
        });
        when(repository.save(any(HarvestDispatch.class))).thenAnswer(i -> {
            HarvestDispatch record = i.getArgument(0);
            stored.put(record.id(), record);
            return record;
        });
        when(access.canAccess(any(), any(), anyString(), nullable(String.class))).thenAnswer(i -> {
            CultivationActor actor = i.getArgument(0);
            FarmPermissionType permission = i.getArgument(1);
            String farm = i.getArgument(2);
            String zone = i.getArgument(3);
            return "FARMER".equals(actor.role()) && "fixture-farm".equals(farm) &&
                    (zone == null || "fixture-zone".equals(zone)) &&
                    ("owner".equals(actor.userId()) || ("viewer".equals(actor.userId()) && permission == FarmPermissionType.VIEW_CARE_SCHEDULE));
        });
        var service = new HarvestDispatchService(batches, releases, seasons, repository, new CultivationAccessGuard(access));
        mvc = MockMvcBuilders.standaloneSetup(new HarvestDispatchController(service))
                .setControllerAdvice(new CultivationApiExceptionHandler()).build();
        evidence = new LinkedHashMap<>(Map.of("harvestSourceRef", "FIXTURE-h", "gradingSourceRef", "FIXTURE-g",
                "packingSourceRef", "FIXTURE-p", "dispatchSourceRef", "FIXTURE-d", "harvestedQuantity", 100,
                "qualifiedQuantity", 90, "rejectedQuantity", 10, "packedQuantity", 80, "shippedQuantity", 70, "quantityUnit", "KG"));
        evidence.put("packedAt", "2025-10-02T00:00:00Z");
        evidence.put("shippedAt", "2025-10-03T00:00:00Z");
        evidence.put("destination", "FIXTURE_ONLY_NOT_A_REAL_RECEIVER");
    }

    @Test void createReadConfirmAndAggregateThroughRealControllerServiceAndGuard() throws Exception {
        create("owner").andExpect(status().isOk()).andExpect(jsonPath("$.confirmedAt").doesNotExist());
        mvc.perform(as("owner", get(BASE + "/fixture-lot/dispatch"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recordedBy").value("owner")).andExpect(jsonPath("$.farmId").value("fixture-farm"));
        summary("owner").andExpect(jsonPath("$.status").value("NO_VERIFIED_DATA"))
                .andExpect(jsonPath("$.quantitiesByUnit").doesNotExist());
        confirm("owner").andExpect(status().isOk()).andExpect(jsonPath("$.confirmedBy").value("owner"));
        summary("owner").andExpect(jsonPath("$.status").value("OPERATOR_CONFIRMED_RECORDS"))
                .andExpect(jsonPath("$.quantitiesByUnit.KG.shipped").value(70))
                .andExpect(jsonPath("$.quantitiesByUnit.KG.qualityPassRate").value(90));
        create("owner").andExpect(status().isOk());
        confirm("owner").andExpect(status().isOk());
        assertThat(stored).hasSize(1);
        verify(repository, times(1)).insert(any(HarvestDispatch.class));
        verify(repository, times(1)).save(any(HarvestDispatch.class));
    }

    @Test void anotherUserCannotCreateReadConfirmOrSeeFarmSummary() throws Exception {
        create("owner").andExpect(status().isOk());
        create("intruder").andExpect(status().isForbidden());
        mvc.perform(as("intruder", get(BASE + "/fixture-lot/dispatch"))).andExpect(status().isForbidden());
        confirm("intruder").andExpect(status().isForbidden());
        mvc.perform(as("intruder", get(BASE + "/dispatch-summary").param("farmId", "fixture-farm")))
                .andExpect(status().isForbidden());
        assertThat(stored.get("fixture-lot").confirmedAt()).isNull();
    }

    @Test void viewPermissionDoesNotAuthorizeConfirmation() throws Exception {
        create("owner").andExpect(status().isOk());
        confirm("viewer").andExpect(status().isForbidden());
        summary("owner").andExpect(jsonPath("$.status").value("NO_VERIFIED_DATA"));
    }

    @Test void invalidWeightAndChronologyCannotPersistOrContribute() throws Exception {
        evidence.put("shippedQuantity", 81);
        create("owner").andExpect(status().isBadRequest());
        evidence.put("shippedQuantity", 70);
        evidence.put("shippedAt", "2025-09-01T00:00:00Z");
        create("owner").andExpect(status().isBadRequest());
        assertThat(stored).isEmpty();
        summary("owner").andExpect(jsonPath("$.status").value("NO_VERIFIED_DATA"));
    }

    @Test void releaseForAnotherLotAndInvalidSeasonAreRejected() throws Exception {
        when(releases.findById("fixture-release")).thenReturn(Optional.of(new ExportRelease("fixture-release", "FIXTURE",
                "wrong-lot", "VN", Map.of(), Map.of(), ExportReleaseStatus.RELEASED, null, null, null, null, null,
                Instant.parse("2025-10-01T00:00:00Z"), null)));
        create("owner").andExpect(status().isBadRequest());
        when(seasons.findById("fixture-season")).thenReturn(Optional.empty());
        create("owner").andExpect(status().isBadRequest());
        assertThat(stored).isEmpty();
    }

    @Test void storedScopeMismatchCannotBeReadOrConfirmedOrCounted() throws Exception {
        create("owner").andExpect(status().isOk());
        HarvestDispatch r = stored.get("fixture-lot");
        stored.put(r.id(), new HarvestDispatch(r.id(), r.exportReleaseId(), r.farmId(), "wrong-zone", r.cultivationSeasonId(),
                r.evidence(), r.recordedBy(), r.recordedAt(), "owner", Instant.now(), r.version()));
        mvc.perform(as("owner", get(BASE + "/fixture-lot/dispatch"))).andExpect(status().isBadRequest());
        confirm("owner").andExpect(status().isBadRequest());
        summary("owner").andExpect(jsonPath("$.status").value("NO_VERIFIED_DATA"));
    }

    @Test void missingAuthenticatedIdentityCannotWrite() throws Exception {
        mvc.perform(post(BASE + "/fixture-lot/dispatch").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/fixture-lot/dispatch/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceDocumentsChecked\":true}")).andExpect(status().isUnauthorized());
        assertThat(stored).isEmpty();
    }

    private String body() throws Exception {
        return json.writeValueAsString(Map.of("exportReleaseId", "fixture-release", "evidence", evidence,
                "farmId", "spoofed", "zoneId", "spoofed", "confirmedBy", "spoofed"));
    }
    private org.springframework.test.web.servlet.ResultActions create(String user) throws Exception {
        return mvc.perform(as(user, post(BASE + "/fixture-lot/dispatch")).contentType(MediaType.APPLICATION_JSON).content(body()));
    }
    private org.springframework.test.web.servlet.ResultActions confirm(String user) throws Exception {
        return mvc.perform(as(user, post(BASE + "/fixture-lot/dispatch/confirm")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceDocumentsChecked\":true,\"confirmedBy\":\"spoofed\"}"));
    }
    private org.springframework.test.web.servlet.ResultActions summary(String user) throws Exception {
        return mvc.perform(as(user, get(BASE + "/dispatch-summary").param("farmId", "fixture-farm"))).andExpect(status().isOk());
    }
    private MockHttpServletRequestBuilder as(String user, MockHttpServletRequestBuilder request) {
        return request.header("X-Auth-User-Id", user).header("X-Auth-Role", "FARMER");
    }
}
