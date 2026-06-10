package com.duriancare.cultivation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum CultivationTaskStatus {
    PLANNED("planned"),
    IN_PROGRESS("in-progress"),
    DONE("done");

    private final String value;

    CultivationTaskStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static CultivationTaskStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported cultivation task status: " + value));
    }
}
