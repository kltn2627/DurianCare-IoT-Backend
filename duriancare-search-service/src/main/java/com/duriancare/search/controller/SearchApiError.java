package com.duriancare.search.controller;

import java.time.Instant;

public record SearchApiError(
        Instant timestamp,
        int status,
        String error,
        String message) {
}
