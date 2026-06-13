package com.duriancare.notification.dto;

public record OtpResponse(String status, boolean valid, String message) {
}
