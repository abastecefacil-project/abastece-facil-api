package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;

/**
 * Quais usuários o autor enxerga: ADMINISTRADOR vê todos, GESTOR_FROTA vê a própria
 * regional, e qualquer outro caso vê apenas a si mesmo.
 *
 * <p>O gestor <b>sem</b> regional cai no último caso: não há escopo com que filtrar, e o
 * caminho permissivo seria mostrar-lhe o sistema inteiro. Mesma postura de
 * {@code UserService.autorizarSobreUsuario}.
 *
 * <p><b>Por que um tipo à parte.</b> A regra tem dois consumidores que precisam de consultas
 * diferentes sobre o mesmo escopo: a listagem ({@code UserService.getAllUsers}) pagina, e o
 * resumo da Home ({@code DashboardService}) conta. Extrair só a <i>decisão</i> mantém a
 * regra num lugar e deixa cada um escolher a sua consulta. A interface é selada para que o
 * {@code switch} dos consumidores seja exaustivo: um quarto escopo quebra a compilação dos
 * dois, em vez de cair num ramo padrão em silêncio.
 *
 * <p>Perfil e regional vêm da entidade, resolvida do banco pelo
 * {@link UsuarioAutenticadoProvider} — nunca do JWT (§6, item 20 do CLAUDE.md).
 */
public sealed interface EscopoUsuarios {

    record Todos() implements EscopoUsuarios {
    }

    record DaRegional(Long regionalId) implements EscopoUsuarios {
    }

    record Proprio(Long userId) implements EscopoUsuarios {
    }

    static EscopoUsuarios de(User autor) {
        if (Perfil.ADMINISTRADOR.equals(autor.getPerfil())) {
            return new Todos();
        }

        if (Perfil.GESTOR_FROTA.equals(autor.getPerfil()) && autor.getRegional() != null) {
            return new DaRegional(autor.getRegional().getId());
        }

        return new Proprio(autor.getId());
    }
}
