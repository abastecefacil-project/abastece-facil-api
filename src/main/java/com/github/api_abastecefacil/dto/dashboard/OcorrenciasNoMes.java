package com.github.api_abastecefacil.dto.dashboard;

/**
 * @param mes   no formato {@code YYYY-MM}
 * @param total ocorrências com {@code occurrenceDate} no mês; 0 quando não houve nenhuma
 */
public record OcorrenciasNoMes(
        String mes,
        long total
) {
}
