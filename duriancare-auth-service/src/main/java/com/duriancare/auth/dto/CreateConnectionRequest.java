package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserConnectionSource;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateConnectionRequest(
        @NotNull UUID receiverId,
        @NotNull UserConnectionSource source) {
}
