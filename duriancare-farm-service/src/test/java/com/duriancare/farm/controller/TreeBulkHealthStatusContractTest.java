package com.duriancare.farm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.TreeHealthStatus;
import com.duriancare.farm.domain.TreeStatus;
import com.duriancare.farm.repository.DurianTreeRepository;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.repository.TreeDiagnosisRecordRepository;
import com.duriancare.farm.service.TreeCarePlanService;
import com.duriancare.farm.service.TreeDiagnosisService;
import com.duriancare.farm.service.TreeService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** PATCH /api/trees/health-status/bulk with trusted Gateway headers and in-memory repositories. */
class TreeBulkHealthStatusContractTest {
    private final Instant now = Instant.parse("2026-10-22T00:00:00Z");
    private final Map<String, DurianTree> store = new HashMap<>();
    private DurianTreeRepository trees;
    private TreeDiagnosisRecordRepository diagnoses;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        FarmRepository farms = mock(FarmRepository.class);
        trees = mock(DurianTreeRepository.class);
        diagnoses = mock(TreeDiagnosisRecordRepository.class);
        when(farms.findById("farm-own")).thenReturn(Optional.of(farm("farm-own", "owner")));
        when(farms.findById("farm-other")).thenReturn(Optional.of(farm("farm-other", "someone-else")));
        when(trees.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
        when(trees.save(any())).thenAnswer(i -> {
            DurianTree saved = i.getArgument(0);
            store.put(saved.id(), saved);
            return saved;
        });
        when(diagnoses.findTopByTreeIdOrderByDiagnosedAtDesc(anyString())).thenReturn(Optional.empty());
        when(diagnoses.countByTreeId(anyString())).thenReturn(0L);
        when(diagnoses.findByTreeIdOrderByDiagnosedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        TreeService service = new TreeService(farms, trees, diagnoses);
        mvc = MockMvcBuilders.standaloneSetup(new TreeController(service,
                mock(TreeDiagnosisService.class), mock(TreeCarePlanService.class), new RequestActorResolver()))
                .setControllerAdvice(new FarmApiExceptionHandler()).build();
    }

    private Farm farm(String id, String owner) {
        return new Farm(id, owner, "Fixture", null, null, null, null, null, null, null, List.of(), now, now);
    }

    private void putTree(String id, String farmId, TreeHealthStatus health, TreeStatus status) {
        store.put(id, new DurianTree(id, farmId, "zone", null, "CODE-" + id, "nick", "Ri6", null,
                null, null, .2, .4, health, status, "note", now, now));
    }

    private ResultActions bulk(String body) throws Exception {
        return mvc.perform(patch("/api/trees/health-status/bulk")
                .header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void updatesEveryOwnedActiveTreeAndChangesOnlyHealthStatus() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        putTree("t2", "farm-own", TreeHealthStatus.SUSPECTED, TreeStatus.ACTIVE);
        bulk("{\"treeIds\":[\"t1\",\"t2\"],\"healthStatus\":\"TREATING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results[0].updated").value(true))
                .andExpect(jsonPath("$.results[0].treeCode").value("CODE-t1"))
                .andExpect(jsonPath("$.results[0].healthStatus").value("TREATING"));
        DurianTree t1 = store.get("t1");
        assertThat(t1.healthStatus()).isEqualTo(TreeHealthStatus.TREATING);
        assertThat(t1.treeCode()).isEqualTo("CODE-t1");
        assertThat(t1.nickname()).isEqualTo("nick");
        assertThat(t1.positionX()).isEqualTo(.2);
        assertThat(t1.status()).isEqualTo(TreeStatus.ACTIVE);
        assertThat(t1.notes()).isEqualTo("note");
    }

    @Test
    void reportsPartialResultsPerTreeWithoutTouchingOtherFarmsOrInactiveTrees() throws Exception {
        putTree("ok", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        putTree("foreign", "farm-other", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        putTree("removed", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.REMOVED);
        bulk("{\"treeIds\":[\"ok\",\"foreign\",\"missing\",\"removed\"],\"healthStatus\":\"TREATING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(4))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.failed").value(3))
                .andExpect(jsonPath("$.results[0].updated").value(true))
                .andExpect(jsonPath("$.results[1].errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.results[1].treeCode").isEmpty())
                .andExpect(jsonPath("$.results[2].errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.results[3].errorCode").value("NOT_ACTIVE"));
        assertThat(store.get("foreign").healthStatus()).isEqualTo(TreeHealthStatus.DISEASED);
        assertThat(store.get("removed").healthStatus()).isEqualTo(TreeHealthStatus.DISEASED);
        verify(trees, times(1)).save(any());
    }

    @Test
    void recoveredNeedsNoDiagnosisHistoryAndNoDiagnosisRecordIsCreated() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.TREATING, TreeStatus.ACTIVE);
        bulk("{\"treeIds\":[\"t1\"],\"healthStatus\":\"RECOVERED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.results[0].healthStatus").value("RECOVERED"));
        assertThat(store.get("t1").healthStatus()).isEqualTo(TreeHealthStatus.RECOVERED);
        verify(diagnoses, never()).save(any());
    }

    @Test
    void bulkAcceptsEveryHealthStatusAndOnlyChangesHealth() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.HEALTHY, TreeStatus.ACTIVE);
        putTree("t2", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        for (TreeHealthStatus target : TreeHealthStatus.values()) {
            bulk("{\"treeIds\":[\"t1\",\"t2\"],\"healthStatus\":\"" + target + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.healthStatus").value(target.name()))
                    .andExpect(jsonPath("$.updated").value(2))
                    .andExpect(jsonPath("$.failed").value(0));
            for (String id : List.of("t1", "t2")) {
                DurianTree tree = store.get(id);
                assertThat(tree.healthStatus()).isEqualTo(target);
                assertThat(tree.status()).isEqualTo(TreeStatus.ACTIVE);
                assertThat(tree.treeCode()).isEqualTo("CODE-" + id);
                assertThat(tree.positionX()).isEqualTo(.2);
                assertThat(tree.notes()).isEqualTo("note");
            }
        }
        verify(diagnoses, never()).save(any());
    }

    @Test
    void singleTreeEndpointAcceptsEveryHealthStatus() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.TREATING, TreeStatus.ACTIVE);
        for (TreeHealthStatus target : TreeHealthStatus.values()) {
            mvc.perform(patch("/api/trees/t1/health-status").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"healthStatus\":\"" + target + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.healthStatus").value(target.name()));
            assertThat(store.get("t1").healthStatus()).isEqualTo(target);
        }
        verify(diagnoses, never()).save(any());
    }

    @Test
    void invalidStatusValuesAreRejectedOnBothEndpoints() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.TREATING, TreeStatus.ACTIVE);
        for (String body : List.of("{\"healthStatus\":\"DEAD\"}", "{\"healthStatus\":\"healthy\"}", "{\"healthStatus\":null}", "{}")) {
            mvc.perform(patch("/api/trees/t1/health-status").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        bulk("{\"treeIds\":[\"t1\"],\"healthStatus\":\"ACTIVE\"}").andExpect(status().isBadRequest());
        verify(trees, never()).save(any());
    }

    @Test
    void deduplicatesIdsAndValidatesTheRequest() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        bulk("{\"treeIds\":[\"t1\",\"t1\"],\"healthStatus\":\"TREATING\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.results.length()").value(1));
        verify(trees, times(1)).save(any());
        bulk("{\"treeIds\":[],\"healthStatus\":\"TREATING\"}").andExpect(status().isBadRequest());
        bulk("{\"treeIds\":[\"t1\"]}").andExpect(status().isBadRequest());
        bulk("{\"treeIds\":[\"t1\"],\"healthStatus\":\"NOT_A_STATUS\"}").andExpect(status().isBadRequest());
    }

    @Test
    void requiresGatewayIdentityHeaders() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        mvc.perform(patch("/api/trees/health-status/bulk").contentType(MediaType.APPLICATION_JSON)
                .content("{\"treeIds\":[\"t1\"],\"healthStatus\":\"TREATING\"}"))
                .andExpect(status().is4xxClientError());
        verify(trees, never()).save(any());
        assertThat(store.get("t1").healthStatus()).isEqualTo(TreeHealthStatus.DISEASED);
        verify(trees, never()).save(eq(null));
    }

    // Regression: the deployed farm-service lacked this route and the catch-all handler turned
    // Spring's NoResourceFoundException into a 500, shown to farmers as "Dịch vụ cây đang gặp lỗi".
    @Test
    void unmappedRouteIsA404NotAServerError() {
        var response = new FarmApiExceptionHandler().handleNoRoute(
                new NoResourceFoundException(HttpMethod.PATCH, "api/trees/health-status/bulk"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Endpoint not found").containsEntry("status", 404);
    }

    @Test
    void singleTreeTransitionStillWorksAndSharesTheRules() throws Exception {
        putTree("t1", "farm-own", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        putTree("foreign", "farm-other", TreeHealthStatus.DISEASED, TreeStatus.ACTIVE);
        mvc.perform(patch("/api/trees/t1/health-status").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"healthStatus\":\"TREATING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.healthStatus").value("TREATING"));
        mvc.perform(patch("/api/trees/foreign/health-status").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"healthStatus\":\"TREATING\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(patch("/api/trees/missing/health-status").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"healthStatus\":\"TREATING\"}"))
                .andExpect(status().isNotFound());
        // No diagnosis history is required to confirm recovery.
        mvc.perform(patch("/api/trees/t1/health-status").header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"healthStatus\":\"RECOVERED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.healthStatus").value("RECOVERED"));
    }

    @Test
    void malformedJsonIsA400WithTheStandardErrorShape() throws Exception {
        bulk("{\"treeIds\":[\"t1\"],").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        verify(trees, never()).save(any());
    }
}
