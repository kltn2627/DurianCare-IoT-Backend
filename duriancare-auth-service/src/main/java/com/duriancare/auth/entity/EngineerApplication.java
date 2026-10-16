package com.duriancare.auth.entity;

import com.duriancare.auth.domain.EngineerApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "engineer_applications")
public class EngineerApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 255)
    private String workplace;

    @Column(nullable = false, length = 255)
    private String specialization;

    @Column(name = "years_experience", nullable = false)
    private Integer yearsExperience;

    @Column(nullable = false, length = 2000)
    private String biography;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EngineerApplicationStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "application")
    private List<EngineerApplicationDocument> documents = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EngineerApplication() {
    }

    public EngineerApplication(
            User user,
            String workplace,
            String specialization,
            Integer yearsExperience,
            String biography) {
        this.user = user;
        this.workplace = workplace;
        this.specialization = specialization;
        this.yearsExperience = yearsExperience;
        this.biography = biography;
        this.status = EngineerApplicationStatus.PENDING_REVIEW;
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

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getWorkplace() {
        return workplace;
    }

    public String getSpecialization() {
        return specialization;
    }

    public Integer getYearsExperience() {
        return yearsExperience;
    }

    public String getBiography() {
        return biography;
    }

    public EngineerApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(EngineerApplicationStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public List<EngineerApplicationDocument> getDocuments() {
        return documents;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
