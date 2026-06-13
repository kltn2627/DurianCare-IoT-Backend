package com.duriancare.notification.service;

public interface OtpService {

    void generateOtp(String email);

    boolean validateOtp(String email, String otp);
}
