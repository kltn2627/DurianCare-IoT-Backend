package com.duriancare.farm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.duriancare.farm.domain.*;
import com.duriancare.farm.repository.*;
import com.duriancare.farm.service.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP contract fixture with trusted Gateway headers, not a real authenticated Gateway E2E. */
class TreeMutationContractTest {
    private FarmRepository farms;
    private DurianTreeRepository trees;
    private TreeDiagnosisRecordRepository diagnoses;
    private MockMvc mvc;
    private final Instant now = Instant.parse("2026-10-15T00:00:00Z");

    @BeforeEach
    void setup() {
        farms = mock(FarmRepository.class);
        trees = mock(DurianTreeRepository.class);
        diagnoses = mock(TreeDiagnosisRecordRepository.class);
        TreeService service = new TreeService(farms, trees, diagnoses);
        mvc = MockMvcBuilders.standaloneSetup(new TreeController(service,
                mock(TreeDiagnosisService.class), mock(TreeCarePlanService.class), new RequestActorResolver()))
                .setControllerAdvice(new FarmApiExceptionHandler()).build();
    }

    private Farm farm() {
        return new Farm("farm", "owner", "Fixture", null, null, null, null, null, null, null,
                List.of(), now, now);
    }

    private DurianTree tree(TreeStatus status) {
        return new DurianTree("tree", "farm", "zone", null, "FIXTURE", null, null, null,
                null, null, .2, .4, TreeHealthStatus.HEALTHY, status, null, now, now);
    }

    @Test
    void createReturnsHealthyWithoutDiagnosisEvenWhenAnotherTreeHasHistory() throws Exception {
        when(farms.findByZoneId("zone")).thenReturn(Optional.of(farm()));
        when(trees.save(any())).thenReturn(tree(TreeStatus.ACTIVE));
        mvc.perform(post("/api/zones/zone/trees").header("X-Auth-User-Id", "owner")
                .header("X-Auth-Role", "FARMER").contentType(MediaType.APPLICATION_JSON)
                .content("{\"treeCode\":\"FIXTURE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.healthStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.diagnosisCount").value(0)).andExpect(jsonPath("$.latestDiseaseCode").isEmpty());
        verifyNoInteractions(diagnoses);
    }

    @Test
    void removeAndRetryReturnPersistedStateWithoutDeletingHistory() throws Exception {
        when(farms.findById("farm")).thenReturn(Optional.of(farm()));
        when(trees.findById("tree")).thenReturn(Optional.of(tree(TreeStatus.ACTIVE)), Optional.of(tree(TreeStatus.REMOVED)));
        when(trees.save(any())).thenAnswer(i -> i.getArgument(0));
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/trees/tree").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REMOVED"))
                    .andExpect(jsonPath("$.healthStatus").value("HEALTHY"));
        }
        verify(trees, times(1)).save(any());
        verify(trees, never()).deleteById(any());
        verify(diagnoses, never()).deleteById(any());
    }

    @Test
    void missingAndForeignTreeReturnStructuredErrorsWithoutWrites() throws Exception {
        mvc.perform(delete("/api/trees/missing").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        when(trees.findById("tree")).thenReturn(Optional.of(tree(TreeStatus.ACTIVE)));
        when(farms.findById("farm")).thenReturn(Optional.of(farm()));
        mvc.perform(delete("/api/trees/tree").header("X-Auth-User-Id", "stranger").header("X-Auth-Role", "FARMER"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verify(trees, never()).save(any());
    }

    @Test
    void missingIdentityAndUnsupportedMethodAreNotUnexpected500() throws Exception {
        mvc.perform(delete("/api/trees/tree")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/trees/tree").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(trees);
    }

    @Test
    void failedSaveDoesNotReturnSuccessOrDeleteRecords() throws Exception {
        when(trees.findById("tree")).thenReturn(Optional.of(tree(TreeStatus.ACTIVE)));
        when(farms.findById("farm")).thenReturn(Optional.of(farm()));
        when(trees.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture"));
        mvc.perform(delete("/api/trees/tree").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER"))
                .andExpect(status().isInternalServerError());
        verifyNoInteractions(diagnoses);
        verify(trees, never()).deleteById(any());
    }
}
