package com.duriancare.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "otp_code", nullable = false, length = 100)
    private String otpCode;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "pending_full_name", nullable = false, length = 150)
    private String pendingFullName;

    @Column(name = "pending_phone_number", length = 30)
    private String pendingPhoneNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OtpVerification() {
    }

    public OtpVerification(
            String email,
            String otpCode,
            LocalDateTime expiredAt,
            String pendingFullName,
            String pendingPhoneNumber) {
        this.email = email;
        this.otpCode = otpCode;
        this.expiredAt = expiredAt;
        this.verified = false;
        this.pendingFullName = pendingFullName;
        this.pendingPhoneNumber = pendingPhoneNumber;
    }

    public void renew(String otpCode, LocalDateTime expiredAt) {
        this.otpCode = otpCode;
        this.expiredAt = expiredAt;
        this.verified = false;
    }

    public void markVerified() {
        this.verified = true;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public String getEmail() {
        return email;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getPendingFullName() {
        return pendingFullName;
    }

    public String getPendingPhoneNumber() {
        return pendingPhoneNumber;
    }
}
