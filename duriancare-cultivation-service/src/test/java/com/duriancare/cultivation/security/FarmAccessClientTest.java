package com.duriancare.cultivation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class FarmAccessClientTest {

    private static final CultivationActor ACTOR = new CultivationActor("engineer-1", "engineer@example.com", "ENGINEER");

    @Test
    void callsInternalFarmAccessEndpointWithInternalToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FarmAccessClient client = new FarmAccessClient(builder, "http://farm-service", "secret");
        server.expect(requestTo("http://farm-service/internal/v1/farm-access/check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess("{\"allowed\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.canAccess(ACTOR, FarmPermissionType.VIEW_CARE_SCHEDULE, "farm-1", "zone-1")).isTrue();
        server.verify();
    }

    @Test
    void farmServiceFailureFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FarmAccessClient client = new FarmAccessClient(builder, "http://farm-service", "secret");
        server.expect(requestTo("http://farm-service/internal/v1/farm-access/check"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.canAccess(ACTOR, FarmPermissionType.VIEW_CARE_SCHEDULE, "farm-1", "zone-1"))
                .isInstanceOf(CultivationAccessDeniedException.class);
        server.verify();
    }
}
