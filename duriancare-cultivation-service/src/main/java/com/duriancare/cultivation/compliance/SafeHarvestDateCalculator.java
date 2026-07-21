package com.duriancare.cultivation.compliance;

import com.duriancare.cultivation.domain.ActivityInputUsage;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SafeHarvestDateCalculator {

    public LocalDate safeHarvestDate(LocalDate completedDate, Integer preHarvestIntervalDays) {
        if (completedDate == null) {
            throw new IllegalArgumentException("Completion date is required");
        }
        if (preHarvestIntervalDays == null || preHarvestIntervalDays <= 0) {
            return completedDate;
        }
        return completedDate.plusDays(preHarvestIntervalDays);
    }

    public Optional<LocalDate> earliestSafeHarvestDate(Collection<ActivityInputUsage> usages) {
        if (usages == null || usages.isEmpty()) {
            return Optional.empty();
        }
        return usages.stream()
                .map(ActivityInputUsage::calculatedSafeHarvestDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
    }
}
