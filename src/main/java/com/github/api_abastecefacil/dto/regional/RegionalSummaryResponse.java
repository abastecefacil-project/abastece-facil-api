package com.github.api_abastecefacil.dto.regional;

/**
 * Projeção enxuta de {@code Regional}, para uso aninhado em outros responses.
 * Omite {@code ativo}, {@code createdAt} e {@code updatedAt} de propósito: não têm
 * consumidor fora de {@link RegionalResponse} e criariam acoplamento desnecessário.
 */
public record RegionalSummaryResponse(
        Long id,
        String nome,
        String sigla
) {
}
