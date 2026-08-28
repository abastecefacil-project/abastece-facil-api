package com.github.api_abastecefacil.model;

import static com.github.api_abastecefacil.constants.AuthConstants.ROLE_PREFIX;

/**
 * Perfil de acesso do usuário.
 *
 * <p>A authority registrada no Spring Security é {@code ROLE_<PERFIL>} — por exemplo
 * {@code ROLE_ADMINISTRADOR}. Como o prefixo já vem embutido aqui, o consumo deve ser
 * por {@code hasRole("ADMINISTRADOR")}, que adiciona {@code ROLE_} sozinho.
 * {@code hasAuthority("ADMINISTRADOR")} NÃO funciona: ele exige o nome exato da
 * authority. O equivalente literal por authority seria
 * {@code hasAuthority("ROLE_ADMINISTRADOR")}.
 *
 * <p>{@link #authority()} é a única origem da string de authority no projeto.
 */
public enum Perfil {

    COLABORADOR,
    GESTOR_FROTA,
    ADMINISTRADOR;

    public String authority() {
        return ROLE_PREFIX + name();
    }
}
