package com.github.api_abastecefacil.dto.dashboard;

/**
 * @param cidade como gravada (sem espaços nas pontas); o agrupamento ignora caixa
 * @param uf     em maiúsculas
 * @param total  postos ativos na cidade
 */
public record CidadeComPostos(
        String cidade,
        String uf,
        long total
) {
}
