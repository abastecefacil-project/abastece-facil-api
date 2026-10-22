package com.github.api_abastecefacil.dto.dashboard;

/**
 * @param placa normalizada (sem espaços nas pontas e em maiúsculas), que é a chave do agrupamento
 */
public record VeiculoComOcorrencias(
        String placa,
        long total
) {
}
