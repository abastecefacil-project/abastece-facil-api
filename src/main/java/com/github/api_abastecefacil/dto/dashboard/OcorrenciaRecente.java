package com.github.api_abastecefacil.dto.dashboard;

import java.time.LocalDate;

/**
 * Versão enxuta de {@code IncidentResponse} para a Home: sem {@code userName}, sem
 * {@code description} e sem {@code createdAt}.
 */
public record OcorrenciaRecente(
        Long id,
        String title,
        String carPlate,
        LocalDate occurrenceDate
) {
}
