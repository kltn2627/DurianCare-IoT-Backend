package com.duriancare.auth.service;

import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;

public interface AuthService {

    void register(RegisterRequest request);

    void resendRegistrationOtp(String email);

    void verifyRegistrationOtp(VerifyOtpRequest request);

    AuthenticationResponse login(LoginRequest request);

    void logout(String accessToken);

    AccessTokenResponse refresh(RefreshTokenRequest request);
}
