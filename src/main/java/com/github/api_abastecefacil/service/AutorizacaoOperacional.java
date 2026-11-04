package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import org.springframework.stereotype.Component;

import static com.github.api_abastecefacil.constants.AutorizacaoConstants.PERFIL_NAO_PERMITIDO_ADMINISTRACAO_MESSAGE;
import static com.github.api_abastecefacil.constants.AutorizacaoConstants.PERFIL_NAO_PERMITIDO_CONSULTA_MESSAGE;
import static com.github.api_abastecefacil.constants.AutorizacaoConstants.PERFIL_NAO_PERMITIDO_ESCRITA_MESSAGE;

/**
 * Permissões operacionais resolvidas pelo usuário atual do banco.
 * Postos exigem administração; veículos e ocorrências permitem gestão.
 * O serviço de veículos também confere a regional do recurso.
 */
@Component
public class AutorizacaoOperacional {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    public AutorizacaoOperacional(UsuarioAutenticadoProvider usuarioAutenticadoProvider) {
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
    }

    /** Criar, alterar ou excluir veículo ou ocorrência; devolve o autor para validar a regional. */
    public User autorizarEscrita() {
        return exigirGestao(PERFIL_NAO_PERMITIDO_ESCRITA_MESSAGE);
    }

    /**
     * Consultar veículo, ocorrência ou o resumo da Home pelas rotas autenticadas.
     *
     * @return o autor, já carregado para conferir o perfil — o resumo da Home escopa a
     *         contagem de usuários por ele sem um segundo {@code SELECT}, como
     *         {@link #autorizarAdministracao} faz para o cancelamento da importação
     */
    public User autorizarConsulta() {
        return exigirGestao(PERFIL_NAO_PERMITIDO_CONSULTA_MESSAGE);
    }

    /**
     * Só ADMINISTRADOR. Mesmo padrão de {@code UserService.autorizarAlteracaoStatus}: o perfil vem
     * do banco, e a recusa é {@code PerfilNaoPermitidoException}.
     *
     * @return o autor, já carregado para conferir o perfil — o cancelamento da importação
     *         registra o e-mail dele sem um segundo {@code SELECT}
     */
    public User autorizarAdministracao() {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();

        if (!Perfil.ADMINISTRADOR.equals(autor.getPerfil())) {
            throw new PerfilNaoPermitidoException(PERFIL_NAO_PERMITIDO_ADMINISTRACAO_MESSAGE);
        }
        return autor;
    }

    /**
     * O perfil vem da entidade, resolvida do banco pelo
     * {@link UsuarioAutenticadoProvider} — nunca do JWT. Mesmo motivo do §6, item 20:
     * o token vive até 30 dias para COLABORADOR, então alguém rebaixado de perfil
     * continuaria autorizado até o próximo login.
     */
    private User exigirGestao(String mensagem) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();

        if (Perfil.ADMINISTRADOR.equals(autor.getPerfil())
                || Perfil.GESTOR_FROTA.equals(autor.getPerfil())) {
            return autor;
        }

        throw new PerfilNaoPermitidoException(mensagem);
    }
}
