package com.duriancare.auth.controller;

import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.EngineerApplicationDetailResponse;
import com.duriancare.auth.dto.EngineerApplicationSummaryResponse;
import com.duriancare.auth.dto.EngineerRegistrationRequest;
import com.duriancare.auth.dto.EmailRequest;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.MessageResponse;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.ReviewEngineerApplicationRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.domain.EngineerApplicationStatus;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.security.AuthenticatedUser;
import com.duriancare.auth.exception.InvalidTokenException;
import com.duriancare.auth.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a farmer or legacy expert account")
    ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Registration accepted. Check your email for the OTP."));
    }

    @PostMapping(value = "/register/engineer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Register an engineer with qualification documents")
    ResponseEntity<MessageResponse> registerEngineer(
            @Valid @ModelAttribute EngineerRegistrationRequest request,
            @RequestPart("qualificationFiles") List<MultipartFile> qualificationFiles) {
        authService.registerEngineer(request, qualificationFiles);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse(
                        "Engineer application submitted. Check your email for the OTP and wait for approval."));
    }

    @PostMapping("/otp/resend")
    @Operation(summary = "Resend registration OTP")
    MessageResponse resendOtp(@Valid @RequestBody EmailRequest request) {
        authService.resendRegistrationOtp(request.email());
        return new MessageResponse("A new OTP has been sent.");
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify registration OTP")
    MessageResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        UserStatus status = authService.verifyRegistrationOtp(request);
        return new MessageResponse(status == UserStatus.ACTIVE
                ? "Account has been activated."
                : "Email verified. Engineer account is awaiting administrator approval.");
    }

    @PostMapping({"/admin/users/{userId}/approve-expert", "/admin/users/{userId}/approve-engineer"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve an engineer account by user id")
    MessageResponse approveExpert(Principal principal, @PathVariable UUID userId) {
        authService.approveExpert(userId, resolveUserId(principal));
        return new MessageResponse("Engineer account has been approved.");
    }

    @GetMapping("/admin/engineer-applications")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List engineer applications")
    List<EngineerApplicationSummaryResponse> listEngineerApplications(
            @RequestParam(required = false) EngineerApplicationStatus status) {
        return authService.listEngineerApplications(status);
    }

    @GetMapping("/admin/engineer-applications/{applicationId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get engineer application details")
    EngineerApplicationDetailResponse getEngineerApplication(@PathVariable UUID applicationId) {
        return authService.getEngineerApplication(applicationId);
    }

    @PostMapping("/admin/engineer-applications/{applicationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve an engineer application after document review")
    MessageResponse approveEngineerApplication(Principal principal, @PathVariable UUID applicationId) {
        authService.approveEngineerApplication(applicationId, resolveUserId(principal));
        return new MessageResponse("Engineer application has been approved.");
    }

    @PostMapping("/admin/engineer-applications/{applicationId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject an engineer application with an optional reason")
    MessageResponse rejectEngineerApplication(
            Principal principal,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ReviewEngineerApplicationRequest request) {
        authService.rejectEngineerApplication(applicationId, request, resolveUserId(principal));
        return new MessageResponse("Engineer application has been rejected.");
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user and return JWT tokens")
    AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout the current user")
    MessageResponse logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException("Bearer access token is required");
        }
        authService.logout(authorization.substring(7));
        return new MessageResponse("Logout completed.");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh an access token using a valid refresh token")
    AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.userId();
        }
        throw new InvalidTokenException("Authenticated user is required");
    }
}
