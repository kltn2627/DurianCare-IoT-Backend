package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cultivation_schedules")
@CompoundIndex(
        name = "zone_schedule_idx",
        def = "{'zoneId': 1, 'scheduledDate': 1, 'scheduledTime': 1}")
public class CultivationSchedule {

    @Id
    private String id;

    @Indexed
    private String zoneId;

    @Indexed
    private String cropId;

    private CultivationTaskType type;

    @Indexed
    private CultivationTaskStatus status;

    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    private String materialName;

    private String dosage;

    private String assignee;

    private String safetyInterval;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
