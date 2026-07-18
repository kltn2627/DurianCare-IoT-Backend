package com.duriancare.farm.service;

public class FarmAccessDeniedException extends RuntimeException {

    public FarmAccessDeniedException(String message) {
        super(message);
    }
}
