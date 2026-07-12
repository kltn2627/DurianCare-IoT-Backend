package com.duriancare.notification.controller;

import com.duriancare.notification.dto.GenerateOtpRequest;
import com.duriancare.notification.dto.OtpResponse;
import com.duriancare.notification.dto.ValidateOtpRequest;
import com.duriancare.notification.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Notification OTP", description = "OTP generation and validation endpoints")
@RequestMapping("/api/v1/notification/otp")
public class NotificationController {

    private final OtpService otpService;

    public NotificationController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Generate OTP", description = "Generate and send an OTP to the provided email address.")
    public OtpResponse generate(@Valid @RequestBody GenerateOtpRequest request) {
        otpService.generateOtp(request.email());
        return new OtpResponse("success", false, "OTP sent");
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate OTP", description = "Validate an OTP code for the specified email address.")
    public OtpResponse validate(@Valid @RequestBody ValidateOtpRequest request) {
        boolean valid = otpService.validateOtp(request.email(), request.otp());
        return new OtpResponse("success", valid, valid ? "OTP is valid" : "OTP is invalid");
    }
}
