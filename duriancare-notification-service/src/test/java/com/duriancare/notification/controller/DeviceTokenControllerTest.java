package com.duriancare.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duriancare.notification.dto.DeviceTokenResponse;
import com.duriancare.notification.dto.RegisterDeviceTokenRequest;
import com.duriancare.notification.service.DeviceTokenService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceTokenController.class)
@Import(ApiExceptionHandler.class)
class DeviceTokenControllerTest {

    private static final String URL = "/api/v1/notification/device-tokens";
    private static final String TOKEN = "fGhT1234567890abcdefghij:APA91bH-abc_DEF.1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceTokenService deviceTokenService;

    private static String body(String token, String deviceId, String platform) {
        return """
                {"token":"%s","deviceId":"%s","platform":"%s","appVersion":"1.0.0"}
                """.formatted(token, deviceId, platform);
    }

    @Test
    void registersForTheAuthenticatedUserAndNeverEchoesTheToken() throws Exception {
        when(deviceTokenService.register(eq("user-1"), any())).thenReturn(
                new DeviceTokenResponse("install-0001", "ANDROID", true, Instant.parse("2026-11-04T00:00:00Z")));

        String response = mockMvc.perform(put(URL)
                        .header("X-Auth-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "install-0001", "ANDROID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("install-0001"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(TOKEN);
    }

    @Test
    void userIdInTheBodyIsIgnored() throws Exception {
        when(deviceTokenService.register(any(), any())).thenReturn(
                new DeviceTokenResponse("install-0001", "ANDROID", true, Instant.now()));

        mockMvc.perform(put(URL)
                        .header("X-Auth-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"victim","token":"%s","deviceId":"install-0001","platform":"ANDROID"}
                                """.formatted(TOKEN)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(deviceTokenService).register(owner.capture(), any(RegisterDeviceTokenRequest.class));
        assertThat(owner.getValue()).isEqualTo("user-1");
    }

    @Test
    void missingIdentityIsUnauthorized() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "install-0001", "ANDROID")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/install-0001")).andExpect(status().isUnauthorized());
        verify(deviceTokenService, never()).register(any(), any());
    }

    @Test
    void rejectsAnExpoPushTokenAndOtherMalformedTokens() throws Exception {
        for (String bad : new String[] {"ExponentPushToken[abcdefghijklmnopqrstuvwxyz012345]", "short", "", "has spaces in it which is not a token at all"}) {
            mockMvc.perform(put(URL)
                            .header("X-Auth-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(bad, "install-0001", "ANDROID")))
                    .andExpect(status().isBadRequest());
        }
        verify(deviceTokenService, never()).register(any(), any());
    }

    @Test
    void rejectsInvalidDeviceIdAndUnsupportedPlatform() throws Exception {
        mockMvc.perform(put(URL)
                        .header("X-Auth-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "bad id", "ANDROID")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(URL)
                        .header("X-Auth-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "install-0001", "WEB")))
                .andExpect(status().isBadRequest());
        verify(deviceTokenService, never()).register(any(), any());
    }

    @Test
    void validationErrorDoesNotQuoteTheToken() throws Exception {
        String bad = "ExponentPushToken[abcdefghijklmnopqrstuvwxyz012345]";
        String response = mockMvc.perform(put(URL)
                        .header("X-Auth-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bad, "install-0001", "ANDROID")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("abcdefghijklmnopqrstuvwxyz012345");
    }

    @Test
    void unregisterIsScopedToTheCaller() throws Exception {
        mockMvc.perform(delete(URL + "/install-0001").header("X-Auth-User-Id", "user-1"))
                .andExpect(status().isNoContent());
        verify(deviceTokenService).unregister("user-1", "install-0001");
    }
}
