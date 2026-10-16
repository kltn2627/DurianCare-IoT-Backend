package com.duriancare.cultivation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.cultivation.domain.HarvestBatchStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class HarvestBatchStatusConverterTest {
    private final MongoConfig.StringToHarvestBatchStatusConverter converter =
            new MongoConfig.StringToHarvestBatchStatusConverter();

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_INSPECTION", "UNKNOWN", "", "EXPORTED"})
    void unrecognizedStoredStatusCannotGrantExportEligibility(String status) {
        assertThat(converter.convert(status)).isEqualTo(HarvestBatchStatus.DRAFT);
    }

    @ParameterizedTest
    @EnumSource(HarvestBatchStatus.class)
    void preservesEverySupportedStatus(HarvestBatchStatus status) {
        assertThat(converter.convert(status.name())).isEqualTo(status);
        assertThat(converter.convert(status.name().toLowerCase(java.util.Locale.ROOT))).isEqualTo(status);
    }
}
