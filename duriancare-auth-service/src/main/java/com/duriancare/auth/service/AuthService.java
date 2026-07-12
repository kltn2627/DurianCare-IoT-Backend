package com.duriancare.auth.service;

import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.EngineerApplicationDetailResponse;
import com.duriancare.auth.dto.EngineerApplicationSummaryResponse;
import com.duriancare.auth.dto.EngineerRegistrationRequest;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.ReviewEngineerApplicationRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.domain.EngineerApplicationStatus;
import com.duriancare.auth.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    void register(RegisterRequest request);

    void registerEngineer(EngineerRegistrationRequest request, List<MultipartFile> qualificationFiles);

    void resendRegistrationOtp(String email);

    UserStatus verifyRegistrationOtp(VerifyOtpRequest request);

    void approveExpert(UUID userId);

    void approveExpert(UUID userId, UUID reviewerUserId);

    List<EngineerApplicationSummaryResponse> listEngineerApplications(EngineerApplicationStatus status);

    EngineerApplicationDetailResponse getEngineerApplication(UUID applicationId);

    void approveEngineerApplication(UUID applicationId);

    void approveEngineerApplication(UUID applicationId, UUID reviewerUserId);

    void rejectEngineerApplication(UUID applicationId, ReviewEngineerApplicationRequest request);

    void rejectEngineerApplication(UUID applicationId, ReviewEngineerApplicationRequest request, UUID reviewerUserId);

    AuthenticationResponse login(LoginRequest request);

    void logout(String accessToken);

    AccessTokenResponse refresh(RefreshTokenRequest request);
}
