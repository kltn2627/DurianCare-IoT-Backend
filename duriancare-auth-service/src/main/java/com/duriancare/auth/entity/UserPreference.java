package com.duriancare.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "firebase_notification_enabled", nullable = false)
    private boolean firebaseNotificationEnabled;

    @Column(name = "email_notification_enabled", nullable = false)
    private boolean emailNotificationEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserPreference() {
    }

    public UserPreference(
            User user,
            String language,
            boolean firebaseNotificationEnabled,
            boolean emailNotificationEnabled) {
        this.user = user;
        this.language = language;
        this.firebaseNotificationEnabled = firebaseNotificationEnabled;
        this.emailNotificationEnabled = emailNotificationEnabled;
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

    public String getLanguage() {
        return language;
    }

    public boolean isFirebaseNotificationEnabled() {
        return firebaseNotificationEnabled;
    }

    public boolean isEmailNotificationEnabled() {
        return emailNotificationEnabled;
    }
}
