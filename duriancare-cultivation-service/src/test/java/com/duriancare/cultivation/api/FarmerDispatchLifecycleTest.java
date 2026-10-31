package com.duriancare.cultivation.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.service.*;
import com.duriancare.cultivation.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FarmerDispatchLifecycleTest {
    final HarvestBatchRepository batches=mock(HarvestBatchRepository.class);
    final ExportReleaseRepository releases=mock(ExportReleaseRepository.class);
    final CultivationSeasonRepository seasons=mock(CultivationSeasonRepository.class);
    final HarvestDispatchRepository dispatches=mock(HarvestDispatchRepository.class);
    final AtomicReference<HarvestDispatch> record=new AtomicReference<>();
    final AtomicReference<CultivationSeason> season=new AtomicReference<>();
    HarvestDispatchService service;
    MockMvc mvc;
    final String evidence="{\"exportReleaseId\":\"release\",\"evidence\":{\"harvestSourceRef\":\"fixture-h\",\"gradingSourceRef\":\"fixture-g\",\"packingSourceRef\":\"fixture-p\",\"dispatchSourceRef\":\"fixture-d\",\"harvestedQuantity\":10,\"qualifiedQuantity\":10,\"rejectedQuantity\":0,\"packedQuantity\":10,\"shippedQuantity\":10,\"quantityUnit\":\"KG\",\"packedAt\":\"2026-08-28T03:00:00Z\",\"shippedAt\":\"2026-08-28T04:00:00Z\",\"destination\":\"fixture-only\"}}";
    @BeforeEach void setup() {
        Instant harvest=Instant.parse("2026-08-28T02:00:00Z");
        var access=mock(FarmAccessClient.class);
        when(access.canAccess(any(),any(),any(),any())).thenAnswer(i->((CultivationActor)i.getArgument(0)).userId().equals("owner") && "farm".equals(i.getArgument(2)));
        var guard=new CultivationAccessGuard(access);
        when(batches.findById("lot")).thenReturn(Optional.of(new HarvestBatch("lot","FIXTURE","season","farm","zone",harvest,
                BigDecimal.TEN,"KG","VN",LocalDate.parse("2026-08-27"),RiskLevel.LOW,HarvestBatchStatus.HARVESTED,"owner",harvest)));
        when(batches.findByCultivationSeasonId("season")).thenAnswer(i->List.of(batches.findById("lot").orElseThrow()));
        season.set(new CultivationSeason("season","farm","zone","fixture","DURIAN","Ri6",LocalDate.parse("2026-03-01"),LocalDate.parse("2026-11-30"),"owner",harvest,harvest));
        when(seasons.findById("season")).thenAnswer(i->Optional.of(season.get()));
        when(seasons.save(any())).thenAnswer(i->{season.set(i.getArgument(0));return season.get();});
        when(releases.findById("release")).thenReturn(Optional.of(new ExportRelease("release","FIXTURE","lot","VN",Map.of(),Map.of(),ExportReleaseStatus.RELEASED,"owner",harvest,"owner",harvest,null,harvest,harvest)));
        when(dispatches.findById("lot")).thenAnswer(i->Optional.ofNullable(record.get()));
        when(dispatches.insert(any(HarvestDispatch.class))).thenAnswer(i->{record.set(i.getArgument(0));return record.get();});
        when(dispatches.save(any(HarvestDispatch.class))).thenAnswer(i->{record.set(i.getArgument(0));return record.get();});
        when(dispatches.findByFarmId("farm")).thenAnswer(i->record.get()==null?List.of():List.of(record.get()));
        service=new HarvestDispatchService(batches,releases,seasons,dispatches,guard);
        mvc=MockMvcBuilders.standaloneSetup(new HarvestDispatchController(service),new SeasonProductionController(new SeasonProductionService(seasons,batches,guard)))
                .setControllerAdvice(new CultivationApiExceptionHandler()).build();
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String suffix,String user,String body) {
        return post("/api/v1/harvest-batches/lot/dispatch"+suffix).header("X-Auth-User-Id",user).header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
    @Test void farmerRecordsConfirmsAndDeliversWithoutAdminOrChangingSeason() throws Exception {
        for(int i=0;i<2;i++) mvc.perform(postAs("","owner",evidence)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        for(int i=0;i<2;i++) mvc.perform(postAs("/confirm","owner","{\"sourceDocumentsChecked\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPATCHED"));
        for(int i=0;i<2;i++) mvc.perform(postAs("/delivered","owner","{\"sourceRef\":\"fixture-delivery\",\"deliveredAt\":\"2026-08-29T00:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED")).andExpect(jsonPath("$.delivery.recordedBy").value("owner"));
        verify(dispatches,times(1)).insert(any(HarvestDispatch.class));
        verify(dispatches,times(2)).save(any(HarvestDispatch.class));
        verify(seasons,never()).save(any());
        assertThat(service.summary(new CultivationActor("owner","","FARMER"),"farm",null).quantitiesByUnit().get("KG").shipped()).isEqualByComparingTo("10");
    }
    @Test void deliveryRequiresOwnerConfirmedDispatchAndValidDate() throws Exception {
        mvc.perform(postAs("","owner",evidence)).andExpect(status().isOk());
        mvc.perform(postAs("/delivered","owner","{\"sourceRef\":\"f\",\"deliveredAt\":\"2026-08-29T00:00:00Z\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("/confirm","foreign","{\"sourceDocumentsChecked\":true}")).andExpect(status().isForbidden());
        mvc.perform(postAs("/delivered","foreign","{}")).andExpect(status().isForbidden());
        mvc.perform(postAs("/confirm","owner","{\"sourceDocumentsChecked\":true}")).andExpect(status().isOk());
        mvc.perform(postAs("/delivered","owner","{\"sourceRef\":\"f\",\"deliveredAt\":\"2099-01-01T00:00:00Z\"}")).andExpect(status().isBadRequest());
    }
    @Test void syntheticReleaseNeverContributesVerifiedQuantities() throws Exception {
        mvc.perform(postAs("","owner",evidence)).andExpect(status().isOk());
        mvc.perform(postAs("/confirm","owner","{\"sourceDocumentsChecked\":true}")).andExpect(status().isOk());
        ExportRelease real=releases.findById("release").orElseThrow();
        when(releases.findById("release")).thenReturn(Optional.of(new ExportRelease(real.id(),"DEMO-SYNTHETIC-TEST",real.harvestBatchId(),"VN",Map.of("dataOrigin","DEMO_SYNTHETIC"),Map.of(),real.status(),real.submittedBy(),real.submittedAt(),real.reviewedBy(),real.reviewedAt(),null,real.releasedAt(),real.createdAt())));
        var summary=service.summary(new CultivationActor("owner","","FARMER"),"farm",null);
        assertThat(summary.status()).isEqualTo("NO_VERIFIED_DATA");
        assertThat(summary.quantitiesByUnit()).isNull();assertThat(summary.shippedByMonth()).isNull();
    }
    @Test void seasonTransitionsAreExplicitAndCannotSkipHarvest() throws Exception {
        mvc.perform(patch("/api/v1/cultivation-seasons/season/production-status").header("X-Auth-User-Id","owner").header("X-Auth-Role","FARMER").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
        for(String state:List.of("HARVESTED","COMPLETED")) mvc.perform(patch("/api/v1/cultivation-seasons/season/production-status").header("X-Auth-User-Id","owner").header("X-Auth-Role","FARMER").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\""+state+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productionStatus").value(state));
        mvc.perform(patch("/api/v1/cultivation-seasons/season/production-status").header("X-Auth-User-Id","foreign").header("X-Auth-Role","FARMER").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void missingOrSyntheticHarvestCannotCompleteAnOperationalSeason() throws Exception {
        doReturn(List.of()).when(batches).findByCultivationSeasonId("season");
        mvc.perform(patch("/api/v1/cultivation-seasons/season/production-status").header("X-Auth-User-Id","owner").header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HARVESTED\"}")).andExpect(status().isBadRequest());
        var b=batches.findById("lot").orElseThrow();
        var synthetic=new HarvestBatch(b.id(),"DEMO-SYNTHETIC-LOT",b.cultivationSeasonId(),b.farmId(),b.plotId(),b.harvestedAt(),b.quantity(),b.quantityUnit(),
                b.expectedDestinationMarket(),b.latestSafeHarvestDate(),b.chemicalRiskLevel(),b.status(),b.createdBy(),b.createdAt());
        doReturn(List.of(synthetic)).when(batches).findByCultivationSeasonId("season");
        mvc.perform(patch("/api/v1/cultivation-seasons/season/production-status").header("X-Auth-User-Id","owner").header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HARVESTED\"}")).andExpect(status().isBadRequest());
        verify(seasons,never()).save(any());
    }
}
