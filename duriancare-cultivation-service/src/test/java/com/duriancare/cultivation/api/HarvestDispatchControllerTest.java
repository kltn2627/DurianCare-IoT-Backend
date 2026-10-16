package com.duriancare.cultivation.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.duriancare.cultivation.security.CultivationAccessDeniedException;
import com.duriancare.cultivation.service.HarvestDispatchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HarvestDispatchControllerTest {
    private final HarvestDispatchService service = mock(HarvestDispatchService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new HarvestDispatchController(service))
                .setControllerAdvice(new CultivationApiExceptionHandler()).build();
    }

    @Test void missingIdentityIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/harvest-batches/dispatch-summary").param("farmId", "f"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void summaryPreservesUnknownQuantitiesAsNull() throws Exception {
        when(service.summary(any(), eq("f"), isNull())).thenReturn(
                new HarvestDispatchService.Summary("NO_VERIFIED_DATA", 0, null, null, List.of()));
        mvc.perform(get("/api/v1/harvest-batches/dispatch-summary").param("farmId", "f")
                .header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_VERIFIED_DATA"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"quantitiesByUnit\":null")));
    }

    @Test void deniedScopeIsForbidden() throws Exception {
        when(service.get(any(), eq("other-lot"))).thenThrow(new CultivationAccessDeniedException("denied"));
        mvc.perform(get("/api/v1/harvest-batches/other-lot/dispatch")
                .header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER"))
                .andExpect(status().isForbidden());
    }

    @Test void confirmationUsesOfficialPostBodyAndGatewayIdentity() throws Exception {
        mvc.perform(post("/api/v1/harvest-batches/b/dispatch/confirm")
                .header("X-Auth-User-Id", "owner").header("X-Auth-Role", "FARMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceDocumentsChecked\":true}"))
                .andExpect(status().isOk());
        verify(service).confirm(new com.duriancare.cultivation.security.CultivationActor("owner", "", "FARMER"), "b", true);
    }
}
