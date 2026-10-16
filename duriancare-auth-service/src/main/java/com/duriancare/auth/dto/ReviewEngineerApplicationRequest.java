package com.duriancare.auth.dto;

import jakarta.validation.constraints.Size;

public record ReviewEngineerApplicationRequest(
        @Size(max = 1000) String rejectionReason) {
}
