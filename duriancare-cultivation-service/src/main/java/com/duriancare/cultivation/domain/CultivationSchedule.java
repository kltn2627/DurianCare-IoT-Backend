package com.duriancare.cultivation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "cultivation_schedules")
public class CultivationSchedule {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String zoneId;

    @Column(nullable = false, length = 64)
    private String cropId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CultivationTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CultivationTaskStatus status;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private LocalTime scheduledTime;

    @Column(nullable = false, length = 160)
    private String materialName;

    @Column(nullable = false, length = 80)
    private String dosage;

    @Column(nullable = false, length = 120)
    private String assignee;

    @Column(nullable = false, length = 80)
    private String safetyInterval;

    @Column(nullable = false, length = 500)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        id = id == null ? UUID.randomUUID() : id;
        status = status == null ? CultivationTaskStatus.PLANNED : status;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getCropId() { return cropId; }
    public void setCropId(String cropId) { this.cropId = cropId; }
    public CultivationTaskType getType() { return type; }
    public void setType(CultivationTaskType type) { this.type = type; }
    public CultivationTaskStatus getStatus() { return status; }
    public void setStatus(CultivationTaskStatus status) { this.status = status; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public String getSafetyInterval() { return safetyInterval; }
    public void setSafetyInterval(String safetyInterval) { this.safetyInterval = safetyInterval; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
