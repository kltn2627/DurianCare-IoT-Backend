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

@RestController
@RequestMapping("/api/v1/notification/otp")
public class NotificationController {

    private final OtpService otpService;

    public NotificationController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpResponse generate(@Valid @RequestBody GenerateOtpRequest request) {
        otpService.generateOtp(request.email());
        return new OtpResponse("success", false, "OTP sent");
    }

    @PostMapping("/validate")
    public OtpResponse validate(@Valid @RequestBody ValidateOtpRequest request) {
        boolean valid = otpService.validateOtp(request.email(), request.otp());
        return new OtpResponse("success", valid, valid ? "OTP is valid" : "OTP is invalid");
    }
}
