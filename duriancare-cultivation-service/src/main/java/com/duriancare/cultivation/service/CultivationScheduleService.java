package com.duriancare.cultivation.service;

import com.duriancare.cultivation.api.CreateCultivationScheduleRequest;
import com.duriancare.cultivation.domain.CultivationSchedule;
import com.duriancare.cultivation.domain.CultivationTaskStatus;
import com.duriancare.cultivation.domain.CultivationTaskType;
import com.duriancare.cultivation.repository.CultivationScheduleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CultivationScheduleService {

    private final CultivationScheduleRepository repository;

    public CultivationScheduleService(CultivationScheduleRepository repository) {
        this.repository = repository;
    }

    public List<CultivationSchedule> findAll(String zoneId, CultivationTaskType type, CultivationTaskStatus status) {
        return repository.findAll(Sort.by("scheduledDate").ascending().and(Sort.by("scheduledTime").ascending()))
                .stream()
                .filter(schedule -> zoneId == null || schedule.getZoneId().equalsIgnoreCase(zoneId))
                .filter(schedule -> type == null || schedule.getType() == type)
                .filter(schedule -> status == null || schedule.getStatus() == status)
                .toList();
    }

    public CultivationSchedule get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new CultivationScheduleNotFoundException(id));
    }

    public CultivationSchedule create(CreateCultivationScheduleRequest request) {
        Instant now = Instant.now();
        CultivationSchedule schedule = new CultivationSchedule();
        schedule.setId(UUID.randomUUID().toString());
        schedule.setZoneId(request.zoneId().trim());
        schedule.setCropId(request.cropId().trim());
        schedule.setType(request.type());
        schedule.setStatus(CultivationTaskStatus.PLANNED);
        schedule.setScheduledDate(request.scheduledAt().toLocalDate());
        schedule.setScheduledTime(request.scheduledAt().toLocalTime());
        schedule.setMaterialName(request.materialName().trim());
        schedule.setDosage(request.dosage().trim());
        schedule.setAssignee(request.assignee().trim());
        schedule.setSafetyInterval(request.safetyInterval().trim());
        schedule.setNotes(request.notes().trim());
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        return repository.save(schedule);
    }

    public CultivationSchedule updateStatus(String id, CultivationTaskStatus status) {
        CultivationSchedule schedule = get(id);
        schedule.setStatus(status);
        schedule.setUpdatedAt(Instant.now());
        return repository.save(schedule);
    }

    public void delete(String id) {
        repository.delete(get(id));
    }

    public String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
