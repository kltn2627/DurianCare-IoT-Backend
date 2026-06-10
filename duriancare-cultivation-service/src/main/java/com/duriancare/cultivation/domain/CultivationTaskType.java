package com.duriancare.cultivation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum CultivationTaskType {
    FERTILIZER("fertilizer"),
    PESTICIDE("pesticide"),
    IRRIGATION("irrigation"),
    PRUNING("pruning"),
    INSPECTION("inspection");

    private final String value;

    CultivationTaskType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static CultivationTaskType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported cultivation task type: " + value));
    }
}
