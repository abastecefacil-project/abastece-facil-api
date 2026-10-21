package com.github.api_abastecefacil.dto.dashboard;

/**
 * Resumo da Home de gestão, devolvido por {@code GET /api/dashboard}.
 *
 * <p>Postos, veículos e ocorrências são globais: essas tabelas não têm regional. Só o bloco
 * de usuários é escopado pelo perfil de quem chama, com a mesma regra de
 * {@code GET /api/users}.
 */
public record DashboardResponse(
        ResumoPostos postos,
        ResumoVeiculos veiculos,
        ResumoOcorrencias ocorrencias,
        ResumoUsuarios usuarios
) {
}
