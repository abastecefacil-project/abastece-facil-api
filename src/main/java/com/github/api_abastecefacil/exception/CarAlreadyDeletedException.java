package com.github.api_abastecefacil.exception;

public class CarAlreadyDeletedException extends RuntimeException {
    public CarAlreadyDeletedException(String message) {
        super(message);
    }
}
