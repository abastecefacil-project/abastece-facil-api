package com.github.api_abastecefacil.dto.incident;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IncidentResponse(
        Long id,
        String carPlate,
        String userName,
        LocalDate occurrenceDate,
        String title,
        String description,
        LocalDateTime createdAt
) {
}
