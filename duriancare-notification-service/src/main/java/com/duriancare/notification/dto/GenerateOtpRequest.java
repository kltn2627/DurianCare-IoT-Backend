package com.duriancare.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GenerateOtpRequest(@NotBlank @Email String email) {
}
