package com.duriancare.farm.dto;

import jakarta.validation.constraints.Size;

public record RejectAgronomistInvitationRequest(
        @Size(max = 1000) String reason) {
}
