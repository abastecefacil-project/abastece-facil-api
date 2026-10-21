package com.github.api_abastecefacil.dto.dashboard;

/**
 * Contadas pela data do fato ({@code occurrenceDate}), não pela data do registro. O mês é o
 * do calendário de São Paulo.
 */
public record ResumoOcorrencias(
        long mesAtual,
        long mesAnterior
) {
}
