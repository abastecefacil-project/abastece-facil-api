package com.github.api_abastecefacil.dto.car;

import com.github.api_abastecefacil.dto.regional.RegionalSummaryResponse;
import java.time.LocalDateTime;

public record CarResponse(
        Long id,
        String licensePlate,
        String model,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RegionalSummaryResponse regional
) {
}
