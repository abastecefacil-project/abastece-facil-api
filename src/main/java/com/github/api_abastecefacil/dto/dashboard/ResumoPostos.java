package com.github.api_abastecefacil.dto.dashboard;

/**
 * @param cidadesAtendidas cidades distintas (par cidade + UF) entre os postos ativos
 */
public record ResumoPostos(
        long ativos,
        long inativos,
        long cidadesAtendidas
) {
}
