package com.duriancare.auth.service;

import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.domain.UserStatus;
import java.util.UUID;

public interface AuthService {

    void register(RegisterRequest request);

    void resendRegistrationOtp(String email);

    UserStatus verifyRegistrationOtp(VerifyOtpRequest request);

    void approveExpert(UUID userId);

    AuthenticationResponse login(LoginRequest request);

    void logout(String accessToken);

    AccessTokenResponse refresh(RefreshTokenRequest request);
}
