package com.duriancare.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EngineerRegistrationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+() .-]{8,30}$") String phoneNumber,
        @NotBlank @Size(max = 255) String workplace,
        @NotBlank @Size(max = 255) String specialization,
        @NotNull @Min(0) @Max(60) Integer yearsExperience,
        @NotBlank @Size(max = 2000) String biography) {
}
