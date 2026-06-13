package com.duriancare.cultivation.service;

public class CultivationScheduleNotFoundException extends RuntimeException {

    public CultivationScheduleNotFoundException(String id) {
        super("Cultivation schedule not found: " + id);
    }
}
