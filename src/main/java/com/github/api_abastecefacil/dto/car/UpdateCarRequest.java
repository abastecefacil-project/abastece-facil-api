package com.github.api_abastecefacil.dto.car;

public record UpdateCarRequest(
        String licensePlate,
        String model,
        Boolean isActive
) {
}
