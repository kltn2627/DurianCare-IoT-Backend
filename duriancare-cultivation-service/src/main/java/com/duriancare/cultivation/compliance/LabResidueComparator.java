package com.duriancare.cultivation.compliance;

import com.duriancare.cultivation.domain.LabResultStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class LabResidueComparator {

    public LabResultStatus compare(BigDecimal measuredValue, BigDecimal detectionLimit, BigDecimal quantificationLimit, BigDecimal mrlValue) {
        if (measuredValue == null) {
            return LabResultStatus.NOT_DETECTED;
        }
        if (detectionLimit != null && measuredValue.compareTo(detectionLimit) < 0) {
            return LabResultStatus.NOT_DETECTED;
        }
        if (quantificationLimit != null && measuredValue.compareTo(quantificationLimit) < 0) {
            return LabResultStatus.BELOW_LIMIT_OF_QUANTIFICATION;
        }
        if (mrlValue == null) {
            return LabResultStatus.NO_STANDARD_FOUND;
        }
        return measuredValue.compareTo(mrlValue) <= 0 ? LabResultStatus.PASS : LabResultStatus.FAIL;
    }
}
