package com.duriancare.farm.service;

public class FarmNotFoundException extends RuntimeException {

    public FarmNotFoundException(String message) {
        super(message);
    }
}
