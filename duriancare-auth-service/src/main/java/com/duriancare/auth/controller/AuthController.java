package com.duriancare.auth.controller;

import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.EmailRequest;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.MessageResponse;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.exception.InvalidTokenException;
import com.duriancare.auth.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Registration accepted. Check your email for the OTP."));
    }

    @PostMapping("/otp/resend")
    MessageResponse resendOtp(@Valid @RequestBody EmailRequest request) {
        authService.resendRegistrationOtp(request.email());
        return new MessageResponse("A new OTP has been sent.");
    }

    @PostMapping("/otp/verify")
    MessageResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        UserStatus status = authService.verifyRegistrationOtp(request);
        return new MessageResponse(status == UserStatus.ACTIVE
                ? "Account has been activated."
                : "Email verified. Expert account is awaiting administrator approval.");
    }

    @PostMapping("/admin/users/{userId}/approve-expert")
    @PreAuthorize("hasRole('ADMIN')")
    MessageResponse approveExpert(@PathVariable UUID userId) {
        authService.approveExpert(userId);
        return new MessageResponse("Expert account has been approved.");
    }

    @PostMapping("/login")
    AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    MessageResponse logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException("Bearer access token is required");
        }
        authService.logout(authorization.substring(7));
        return new MessageResponse("Logout completed.");
    }

    @PostMapping("/refresh")
    AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }
}
