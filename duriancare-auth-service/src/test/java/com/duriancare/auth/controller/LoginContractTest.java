package com.duriancare.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.exception.AuthenticationFailedException;
import com.duriancare.auth.exception.GlobalExceptionHandler;
import com.duriancare.auth.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Controller/validation contract fixtures, not live credential or JWT verification. */
class LoginContractTest {
    private final AuthService service = mock(AuthService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void validContractReachesOfficialLogin() throws Exception {
        when(service.login(any())).thenReturn(new AuthenticationResponse(null, null, "Bearer", 60,
                UUID.randomUUID(), "fixture@example.test", UserRole.FARMER, UserStatus.ACTIVE, null));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fixture@example.test\",\"password\":\"fixture-only\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("FARMER"));
        verify(service).login(new LoginRequest("fixture@example.test", "fixture-only"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"email\":\"fixture@@example.test\",\"password\":\"fixture-only\"}",
            "{\"email\":\"invalid\",\"password\":\"fixture-only\"}",
            "{\"password\":\"fixture-only\"}", "{\"email\":\"fixture@example.test\"}"})
    void invalidEmailOrMissingRequiredFieldNeverReachesAuthentication(String body) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void incorrectCredentialsReturnUnauthorizedNotValidationError() throws Exception {
        when(service.login(any())).thenThrow(new AuthenticationFailedException("Email or password is incorrect"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fixture@example.test\",\"password\":\"fixture-only\"}"))
                .andExpect(status().isUnauthorized());
    }
}
