package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.duriancare.cultivation.api.*;
import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.compliance.*;
import com.duriancare.cultivation.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP/service ownership fixture, not a live authenticated Gateway test. */
class FarmerExportWorkflowTest {
    final Map<String, ExportRelease> records = new HashMap<>();
    final HarvestBatchRepository batches = mock(HarvestBatchRepository.class);
    final ExportReleaseRepository releases = mock(ExportReleaseRepository.class);
    final ComplianceEngine engine = mock(ComplianceEngine.class);
    final ComplianceAssessmentRepository assessments = mock(ComplianceAssessmentRepository.class);
    final CultivationSeasonCatalogService catalog = mock(CultivationSeasonCatalogService.class);
    CultivationCalendarService service;
    MockMvc mvc;
    boolean eligible = true;
    final CultivationActor owner = new CultivationActor("owner", "", "FARMER");
    final Instant harvest = Instant.parse("2026-08-28T02:00:00Z");
    final String inspection = "{\"sourceRef\":\"fixture-inspection\",\"inspectedAt\":\"2026-08-28T03:00:00Z\",\"phiChecked\":true,\"pestCheckPassed\":true,\"qualityPassed\":true,\"measuredBrix\":31.5}";

    @BeforeEach void setup() {
        var access = mock(FarmAccessClient.class);
        when(access.canAccess(any(), any(), any(), any())).thenAnswer(i ->
                ((CultivationActor)i.getArgument(0)).userId().equals("owner") && "farm".equals(i.getArgument(2)) && "zone".equals(i.getArgument(3)));
        var guard = new CultivationAccessGuard(access);
        var activities = mock(CultivationActivityRepository.class);
        when(activities.findAll()).thenReturn(List.of());
        when(batches.findById("lot")).thenReturn(Optional.of(new HarvestBatch("lot", "LOT", "season", "farm", "zone",
                harvest, BigDecimal.TEN, "KG", "VN", LocalDate.parse("2026-08-27"), RiskLevel.LOW, HarvestBatchStatus.HARVESTED, "owner", harvest)));
        when(releases.findById(any())).thenAnswer(i->Optional.ofNullable(records.get(i.getArgument(0))));
        when(releases.findByHarvestBatchId("lot")).thenAnswer(i->List.copyOf(records.values()));
        when(releases.save(any())).thenAnswer(i->{ExportRelease r=i.getArgument(0);records.put(r.id(),r);return r;});
        when(engine.assess(any(), any())).thenAnswer(i->new ComplianceDecision(0,RiskLevel.LOW,List.of(),List.of(),!eligible,eligible,eligible));
        when(assessments.save(any())).thenAnswer(i->i.getArgument(0));
        service = new CultivationCalendarService(mock(CultivationPlanRepository.class), activities,
                mock(ActivityExecutionRepository.class),mock(ActivityInputUsageRepository.class),mock(AgriculturalInputRepository.class),
                mock(AuditLogRepository.class),mock(ResidueStandardRepository.class),mock(LabSampleRepository.class),
                mock(LabResidueResultRepository.class),batches,releases,assessments,mock(SafeHarvestDateCalculator.class),
                engine,mock(LabResidueComparator.class),guard);
        mvc=MockMvcBuilders.standaloneSetup(new CultivationCalendarController(service,catalog))
                .setControllerAdvice(new CultivationApiExceptionHandler()).build();
    }
    ExportRelease create() {
        return service.createExportRelease(owner,new CultivationCalendarDtos.CreateExportReleaseRequest("FIXTURE", "lot", "VN", "owner"));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path, String actor, String body) {
        return post(path).header("X-Auth-User-Id",actor).header("X-Auth-Role","FARMER").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test void farmerCreatesSelfInspectsAndReleasesWithoutAdminAndRetriesDoNotSaveAgain() throws Exception {
        mvc.perform(postAs("/api/v1/export-releases","owner","{\"releaseCode\":\"FIXTURE\",\"harvestBatchId\":\"lot\",\"targetMarketCode\":\"VN\",\"submittedBy\":\"owner\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));
        String id=records.keySet().iterator().next();
        for(int i=0;i<2;i++) mvc.perform(postAs("/api/v1/export-releases/"+id+"/inspect","owner",inspection))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSPECTED_PASSED"))
                .andExpect(jsonPath("$.reviewedBy").value("owner"));
        for(int i=0;i<2;i++) mvc.perform(postAs("/api/v1/export-releases/"+id+"/release","owner","{\"userId\":\"owner\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        verify(releases,times(3)).save(any());
        verifyNoInteractions(catalog);
        assertThat(records).hasSize(1);
    }

    @Test void foreignFarmerCannotSelfInspectAndClientCannotChooseReleaseActor() throws Exception {
        var r=create();
        mvc.perform(postAs("/api/v1/export-releases/"+r.id()+"/inspect","foreign",inspection)).andExpect(status().isForbidden());
        mvc.perform(postAs("/api/v1/export-releases/"+r.id()+"/release","owner","{\"userId\":\"forged\"}")).andExpect(status().isBadRequest());
        verify(releases,times(1)).save(any());
    }

    @Test void freshComplianceRejectionCannotBeBypassedByCheckboxesOrReleaseRetry() throws Exception {
        var r=create(); eligible=false;
        mvc.perform(postAs("/api/v1/export-releases/"+r.id()+"/inspect","owner",inspection))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_ELIGIBLE"));
        assertThatThrownBy(()->service.releaseExportRelease(owner,r.id(),"owner")).isInstanceOf(IllegalStateException.class);
        assertThat(records.get(r.id()).releasedAt()).isNull();
        verifyNoInteractions(catalog);
    }

    @Test void missingQualityCheckStaysPending() throws Exception {
        var r=create();
        mvc.perform(postAs("/api/v1/export-releases/"+r.id()+"/inspect","owner",inspection.replace("\"qualityPassed\":true","\"qualityPassed\":false")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSPECTION_PENDING"));
    }

    @Test void futureDatePreHarvestDateAndInvalidBrixAreRejected() throws Exception {
        var r=create();
        for(String payload:List.of(inspection.replace("2026-08-28T03:00:00Z","2099-01-01T00:00:00Z"),
                inspection.replace("2026-08-28T03:00:00Z","2026-08-27T00:00:00Z"),inspection.replace("31.5","101"))) {
            mvc.perform(postAs("/api/v1/export-releases/"+r.id()+"/inspect","owner",payload)).andExpect(status().isBadRequest());
        }
        verify(releases,times(1)).save(any());
    }

    @Test void complianceChangesAfterPassedInspectionBlockActualRelease() {
        var r=create();
        service.inspectExportRelease(owner,r.id(),new CultivationCalendarDtos.SelfInspectionRequest("fixture",harvest.plusSeconds(60),true,true,true,BigDecimal.valueOf(31)));
        eligible=false;
        assertThatThrownBy(()->service.releaseExportRelease(owner,r.id(),"owner")).hasMessageContaining("Current compliance");
        assertThat(records.get(r.id()).status()).isEqualTo(ExportReleaseStatus.INSPECTED_PASSED);
    }

    @Test void legacyApprovalAlsoRechecksComplianceBeforeReleaseAndRemainsRetryable() {
        var r=create();
        service.submitExportRelease(owner,r.id(),"owner");
        service.approveExportRelease(owner,r.id(),"owner");
        eligible=false;
        assertThatThrownBy(()->service.releaseExportRelease(owner,r.id(),"owner")).hasMessageContaining("Current compliance");
        assertThat(records.get(r.id()).status()).isEqualTo(ExportReleaseStatus.APPROVED);
        eligible=true;
        var released=service.releaseExportRelease(owner,r.id(),"owner");
        assertThat(released.status()).isEqualTo(ExportReleaseStatus.RELEASED);
        clearInvocations(releases,assessments);
        assertThat(service.releaseExportRelease(owner,r.id(),"owner")).isEqualTo(released);
        verify(releases,never()).save(any());
        verify(assessments,never()).save(any());
    }

    @Test void legacyApprovalCannotReleaseBeforePhiDate() {
        var r=create();
        service.submitExportRelease(owner,r.id(),"owner");
        service.approveExportRelease(owner,r.id(),"owner");
        var original=batches.findById("lot").orElseThrow();
        when(batches.findById("lot")).thenReturn(Optional.of(new HarvestBatch(original.id(),original.batchCode(),original.cultivationSeasonId(),
                original.farmId(),original.plotId(),original.harvestedAt(),original.quantity(),original.quantityUnit(),original.expectedDestinationMarket(),
                LocalDate.parse("2026-08-29"),original.chemicalRiskLevel(),original.status(),original.createdBy(),original.createdAt())));
        assertThatThrownBy(()->service.releaseExportRelease(owner,r.id(),"owner")).hasMessageContaining("PHI");
        assertThat(records.get(r.id()).releasedAt()).isNull();
    }
}
