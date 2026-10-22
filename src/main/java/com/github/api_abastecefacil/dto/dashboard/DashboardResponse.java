package com.github.api_abastecefacil.dto.dashboard;

import java.util.List;

/**
 * Resumo da Home de gestão, devolvido por {@code GET /api/dashboard}.
 *
 * <p>Postos, veículos e ocorrências são globais: essas tabelas não têm regional. Os campos de
 * usuário ({@code usuarios}, {@code usuariosPorPerfil} e
 * {@code usuariosAguardandoAtivacaoAntigos}) são escopados pelo perfil de quem chama, com a
 * mesma regra de {@code GET /api/users}.
 *
 * @param ocorrenciasPorMes                  sempre {@code MESES_SERIE_OCORRENCIAS} itens, em ordem
 *                                           cronológica, terminando no mês atual
 * @param veiculosComMaisOcorrencias         ranking por placa normalizada, na janela de
 *                                           {@code DIAS_RANKING_VEICULOS} dias
 * @param cidadesComMaisPostos               ranking por postos ativos
 * @param usuariosPorPerfil                  os três perfis, na ordem do enum, com zero nos ausentes
 * @param usuariosAguardandoAtivacaoAntigos  ativos sem senha definida, criados há mais de
 *                                           {@code DIAS_CONVITE_PENDENTE_ANTIGO} dias
 * @param ultimasOcorrencias                 as mais recentes por data de registro
 */
public record DashboardResponse(
        ResumoPostos postos,
        ResumoVeiculos veiculos,
        ResumoOcorrencias ocorrencias,
        ResumoUsuarios usuarios,
        List<OcorrenciasNoMes> ocorrenciasPorMes,
        List<VeiculoComOcorrencias> veiculosComMaisOcorrencias,
        List<CidadeComPostos> cidadesComMaisPostos,
        List<UsuariosNoPerfil> usuariosPorPerfil,
        long usuariosAguardandoAtivacaoAntigos,
        List<OcorrenciaRecente> ultimasOcorrencias
) {
}
