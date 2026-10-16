package com.duriancare.farm.service;

public class FarmInvalidRequestException extends RuntimeException {

    public FarmInvalidRequestException(String message) {
        super(message);
    }
}
