package com.duriancare.auth.service.impl;

import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.entity.OtpVerification;
import com.duriancare.auth.repository.OtpVerificationRepository;
import com.duriancare.auth.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class AuthServiceDebugTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void traceVerifyAndLoginForFreshFarmerAccount() {
        String email = "debug.farmer." + UUID.randomUUID() + "@example.com";
        String password = "DebugFarmer@12345";
        String otp = "123456";

        authService.register(new RegisterRequest(
                email,
                password,
                "Debug Farmer",
                "0900123456",
                UserRole.FARMER));

        OtpVerification verification = otpVerificationRepository.findByEmailIgnoreCase(email)
                .orElseThrow();
        verification.renew(passwordEncoder.encode(otp), verification.getExpiredAt());
        otpVerificationRepository.save(verification);

        try {
            authService.verifyRegistrationOtp(new VerifyOtpRequest(email, otp));
            System.out.println("VERIFY_OK");
        } catch (Exception exception) {
            System.out.println("VERIFY_FAILED=" + exception.getClass().getName() + ": " + exception.getMessage());
            Throwable cause = exception.getCause();
            while (cause != null) {
                System.out.println("CAUSE=" + cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
            return;
        }

        try {
            authService.login(new LoginRequest(email, password));
            System.out.println("LOGIN_OK");
        } catch (Exception exception) {
            System.out.println("LOGIN_FAILED=" + exception.getClass().getName() + ": " + exception.getMessage());
            Throwable cause = exception.getCause();
            while (cause != null) {
                System.out.println("CAUSE=" + cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
        }
    }
}
