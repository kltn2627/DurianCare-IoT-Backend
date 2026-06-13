package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+() .-]{8,30}$") String phoneNumber,
        UserRole role) {
}
