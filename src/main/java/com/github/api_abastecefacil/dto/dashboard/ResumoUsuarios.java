package com.github.api_abastecefacil.dto.dashboard;

/**
 * Escopado pelo perfil de quem chama, com a regra de {@code EscopoUsuarios}.
 *
 * @param aguardandoAtivacao ativos que ainda não definiram a senha pelo convite
 */
public record ResumoUsuarios(
        long ativos,
        long aguardandoAtivacao
) {
}
