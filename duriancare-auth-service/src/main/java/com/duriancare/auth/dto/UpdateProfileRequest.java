package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+() .-]{8,30}$") String phoneNumber,
        @Past LocalDate dateOfBirth,
        UserGender gender,
        @Size(max = 500) String address,
        @Size(max = 150) String provinceCity,
        @Size(max = 500) String bio) {
}
