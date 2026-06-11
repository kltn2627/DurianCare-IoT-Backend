package com.duriancare.cultivation.service;

import com.duriancare.cultivation.api.CreateCultivationScheduleRequest;
import com.duriancare.cultivation.domain.CultivationSchedule;
import com.duriancare.cultivation.domain.CultivationTaskStatus;
import com.duriancare.cultivation.domain.CultivationTaskType;
import com.duriancare.cultivation.repository.CultivationScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CultivationScheduleService {

    private final CultivationScheduleRepository repository;

    public CultivationScheduleService(CultivationScheduleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CultivationSchedule> findAll(String zoneId, CultivationTaskType type, CultivationTaskStatus status) {
        return repository.findAll(Sort.by("scheduledDate").ascending().and(Sort.by("scheduledTime").ascending()))
                .stream()
                .filter(schedule -> zoneId == null || schedule.getZoneId().equalsIgnoreCase(zoneId))
                .filter(schedule -> type == null || schedule.getType() == type)
                .filter(schedule -> status == null || schedule.getStatus() == status)
                .toList();
    }

    @Transactional(readOnly = true)
    public CultivationSchedule get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Cultivation schedule not found"));
    }

    @Transactional
    public CultivationSchedule create(CreateCultivationScheduleRequest request) {
        CultivationSchedule schedule = new CultivationSchedule();
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
        return repository.save(schedule);
    }

    @Transactional
    public CultivationSchedule updateStatus(UUID id, CultivationTaskStatus status) {
        CultivationSchedule schedule = get(id);
        schedule.setStatus(status);
        return schedule;
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    public String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
