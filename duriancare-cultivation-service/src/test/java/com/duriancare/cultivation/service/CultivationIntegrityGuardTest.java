package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.cultivation.api.CultivationCalendarDtos;
import com.duriancare.cultivation.compliance.ComplianceEngine;
import com.duriancare.cultivation.compliance.LabResidueComparator;
import com.duriancare.cultivation.compliance.SafeHarvestDateCalculator;
import com.duriancare.cultivation.domain.*;
import com.duriancare.cultivation.repository.*;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CultivationIntegrityGuardTest {
    @Mock CultivationPlanRepository plans;
    @Mock CultivationSeasonRepository seasons;
    @Mock CultivationActivityRepository activities;
    @Mock ActivityExecutionRepository executions;
    @Mock ActivityInputUsageRepository usages;
    @Mock AgriculturalInputRepository inputs;
    @Mock AuditLogRepository audits;
    @Mock ResidueStandardRepository standards;
    @Mock LabSampleRepository samples;
    @Mock LabResidueResultRepository results;
    @Mock HarvestBatchRepository batches;
    @Mock ExportReleaseRepository releases;
    @Mock ComplianceAssessmentRepository assessments;
    @Mock CultivationAccessGuard access;
    @Mock LegacyIntegrityMarkerRepository integrityMarkers;
    CultivationCalendarService service;

    @BeforeEach
    void setUp() {
        service = new CultivationCalendarService(plans, seasons, activities, executions, usages,
                inputs, audits, standards, samples, results, batches, releases, assessments,
                new SafeHarvestDateCalculator(), new ComplianceEngine(), new LabResidueComparator(), access,
                integrityMarkers);
    }

    @Test
    void orphanPlanIsAbsentFromCanonicalList() {
        when(plans.findAll()).thenReturn(List.of(plan("farm-1", "zone-1")));
        when(seasons.findById("season-1")).thenReturn(Optional.empty());
        assertThat(service.listPlans(null, null, null)).isEmpty();
    }

    @Test
    void planWhoseFarmDiffersFromSeasonIsAbsent() {
        when(plans.findAll()).thenReturn(List.of(plan("farm-2", "zone-1")));
        when(seasons.findById("season-1")).thenReturn(Optional.of(season("farm-1", "zone-1")));
        assertThat(service.listPlans(null, null, null)).isEmpty();
    }

    @Test
    void activityWithWrongZoneCannotBeCreatedThroughRawService() {
        validPlan();
        assertThatThrownBy(() -> service.createActivity(activityRequest("season-1", "zone-2")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Activity scope");
        verify(activities, never()).save(any(CultivationActivity.class));
    }

    @Test
    void activityWithWrongSeasonCannotBeCreatedThroughRawService() {
        validPlan();
        assertThatThrownBy(() -> service.createActivity(activityRequest("season-2", "zone-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Activity scope");
        verify(activities, never()).save(any(CultivationActivity.class));
    }

    @Test
    void orphanActivityIsAbsentEvenWhenSeasonFilterMatches() {
        when(activities.findAll()).thenReturn(List.of(activity()));
        when(plans.findById("plan-1")).thenReturn(Optional.empty());
        assertThat(service.listActivities("season-1", null, null)).isEmpty();
    }

    @Test
    void orphanBatchCannotBeReadOrReleased() {
        when(batches.findById("batch-1")).thenReturn(Optional.of(batch("farm-1", "zone-1")));
        when(seasons.findById("season-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHarvestBatch("batch-1"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(() -> service.createExportRelease(
                new com.duriancare.cultivation.security.CultivationActor("owner", "o@example.com", "FARMER"),
                new CultivationCalendarDtos.CreateExportReleaseRequest("R-1", "batch-1", "VN", "owner")))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(releases, never()).save(any(ExportRelease.class));
    }

    @Test
    void labSampleCannotPointToBatchOfAnotherSeason() {
        when(batches.findById("batch-1")).thenReturn(Optional.of(batch("farm-1", "zone-1")));
        when(seasons.findById("season-1")).thenReturn(Optional.of(season("farm-1", "zone-1")));
        var request = new CultivationCalendarDtos.CreateLabSampleRequest("season-2", "batch-1", "S-1",
                SampleType.FRUIT, Instant.now().minusSeconds(60), "owner", null, "Lab", null,
                LabSampleStatus.COLLECTED, List.of());
        assertThatThrownBy(() -> service.createLabSample(
                new com.duriancare.cultivation.security.CultivationActor("owner", "o@example.com", "FARMER"), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requested cultivation season");
        verify(samples, never()).save(any(LabSample.class));
    }

    @Test
    void releasedLegacyRecordWithBrokenBatchAncestryIsHidden() {
        when(releases.findAll()).thenReturn(List.of(new ExportRelease("release-1", "R-1", "batch-1", "VN",
                Map.of(), Map.of(), ExportReleaseStatus.RELEASED, "owner", null, null, null, null, null, Instant.now())));
        when(batches.findById("batch-1")).thenReturn(Optional.of(batch("farm-1", "zone-1")));
        when(seasons.findById("season-1")).thenReturn(Optional.empty());
        assertThat(service.listExportReleases(null, null, null)).isEmpty();
    }

    @Test
    void legacyReleaseMarkerHidesReleaseIndependentlyOfLaterParentChanges() {
        when(releases.findAll()).thenReturn(List.of(new ExportRelease("release-1", "R-1", "batch-1", "VN",
                Map.of(), Map.of(), ExportReleaseStatus.RELEASED, "owner", null, null, null, null, null, Instant.now())));
        when(integrityMarkers.existsById("export_releases:release-1")).thenReturn(true);
        assertThat(service.listExportReleases(null, null, null)).isEmpty();
    }

    private void validPlan() {
        when(plans.findById("plan-1")).thenReturn(Optional.of(plan("farm-1", "zone-1")));
        when(seasons.findById("season-1")).thenReturn(Optional.of(season("farm-1", "zone-1")));
    }

    private CultivationCalendarDtos.CreateActivityRequest activityRequest(String seasonId, String zoneId) {
        return new CultivationCalendarDtos.CreateActivityRequest("plan-1", seasonId, "farm-1", zoneId,
                List.of(), ActivityType.IRRIGATION, "Water", null, Instant.now(), null, null, null,
                List.of(), false, List.of(), null, null, null);
    }

    private CultivationSeason season(String farmId, String plotId) {
        return new CultivationSeason("season-1", farmId, plotId, "Season", "DURIAN", null,
                LocalDate.now(), null, "owner", Instant.now(), Instant.now());
    }

    private CultivationPlan plan(String farmId, String plotId) {
        return new CultivationPlan("plan-1", farmId, plotId, "season-1", null, "Plan",
                LocalDate.now(), null, List.of("VN"), PlanStatus.ACTIVE, "owner", Instant.now(), Instant.now());
    }

    private CultivationActivity activity() {
        return new CultivationActivity("activity-1", "plan-1", "season-1", "farm-1", "zone-1", List.of(),
                ActivityType.IRRIGATION, "Water", null, Instant.now(), null, null, "NORMAL",
                ActivityStatus.SCHEDULED, List.of(), false, null, null, null, null, Instant.now(), Instant.now());
    }

    private HarvestBatch batch(String farmId, String plotId) {
        return new HarvestBatch("batch-1", "B-1", "season-1", farmId, plotId, Instant.now(),
                BigDecimal.ONE, "kg", "VN", null, RiskLevel.LOW, HarvestBatchStatus.HARVESTED,
                "owner", Instant.now());
    }
}
