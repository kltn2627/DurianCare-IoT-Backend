package com.duriancare.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ValidateOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{4,10}") String otp) {
}
