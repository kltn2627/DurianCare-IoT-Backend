package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lab_samples")
public record LabSample(
        @Id String id,
        @Indexed String cultivationSeasonId,
        @Indexed String harvestBatchId,
        @Indexed(unique = true) String sampleCode,
        SampleType sampleType,
        Instant sampledAt,
        String sampledBy,
        String samplingLocation,
        String laboratoryName,
        String laboratoryAccreditation,
        @Indexed LabSampleStatus status,
        List<String> attachments,
        Instant createdAt) {
}
