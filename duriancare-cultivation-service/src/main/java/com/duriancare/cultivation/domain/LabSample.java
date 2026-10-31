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
        Instant createdAt,
        List<String> expectedActiveIngredientCodes) {

    public LabSample(String id, String cultivationSeasonId, String harvestBatchId, String sampleCode,
            SampleType sampleType, Instant sampledAt, String sampledBy, String samplingLocation,
            String laboratoryName, String laboratoryAccreditation, LabSampleStatus status,
            List<String> attachments, Instant createdAt) {
        this(id, cultivationSeasonId, harvestBatchId, sampleCode, sampleType, sampledAt, sampledBy,
                samplingLocation, laboratoryName, laboratoryAccreditation, status, attachments, createdAt, List.of());
    }

    public LabSample {
        expectedActiveIngredientCodes = expectedActiveIngredientCodes == null
                ? List.of() : List.copyOf(expectedActiveIngredientCodes);
    }
}
