package com.duriancare.cultivation.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.cultivation.domain.LabResultStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LabResidueComparatorTest {

    private final LabResidueComparator comparator = new LabResidueComparator();

    @Test
    void returnsPassWhenMeasuredValueIsWithinMrl() {
        LabResultStatus status = comparator.compare(
                new BigDecimal("0.08"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                new BigDecimal("0.10"));

        assertThat(status).isEqualTo(LabResultStatus.PASS);
    }

    @Test
    void returnsFailWhenMeasuredValueExceedsMrl() {
        LabResultStatus status = comparator.compare(
                new BigDecimal("0.12"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                new BigDecimal("0.10"));

        assertThat(status).isEqualTo(LabResultStatus.FAIL);
    }

    @Test
    void returnsNoStandardFoundWhenMrlIsMissing() {
        LabResultStatus status = comparator.compare(
                new BigDecimal("0.04"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                null);

        assertThat(status).isEqualTo(LabResultStatus.NO_STANDARD_FOUND);
    }

    @Test
    void returnsNotDetectedWhenMeasuredValueIsBelowDetectionLimit() {
        LabResultStatus status = comparator.compare(
                new BigDecimal("0.005"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                new BigDecimal("0.10"));

        assertThat(status).isEqualTo(LabResultStatus.NOT_DETECTED);
    }

    @Test
    void returnsBelowQuantificationLimitWhenDetectedButNotQuantifiable() {
        LabResultStatus status = comparator.compare(
                new BigDecimal("0.015"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                new BigDecimal("0.10"));

        assertThat(status).isEqualTo(LabResultStatus.BELOW_LIMIT_OF_QUANTIFICATION);
    }
}
