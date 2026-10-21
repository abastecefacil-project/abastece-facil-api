package com.github.api_abastecefacil.dto.dashboard;

import com.github.api_abastecefacil.model.Perfil;

/**
 * @param total usuários ativos com o perfil, dentro do escopo de quem chama; 0 quando não há
 */
public record UsuariosNoPerfil(
        Perfil perfil,
        long total
) {
}
