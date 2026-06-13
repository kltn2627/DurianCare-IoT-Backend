package com.duriancare.notification.service;

public interface EmailService {

    void sendOtpEmail(String recipient, String otp, long ttlMinutes);
}
