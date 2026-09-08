package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import org.springframework.stereotype.Component;

import static com.github.api_abastecefacil.constants.AutorizacaoConstants.PERFIL_NAO_PERMITIDO_CONSULTA_MESSAGE;
import static com.github.api_abastecefacil.constants.AutorizacaoConstants.PERFIL_NAO_PERMITIDO_ESCRITA_MESSAGE;

/**
 * Autorização por perfil do <b>cadastro operacional</b> — postos, veículos e ocorrências.
 * Ponto único da regra: ADMINISTRADOR e GESTOR_FROTA podem, COLABORADOR não.
 *
 * <p><b>Por que não é {@code @PreAuthorize}.</b> O P0.4 mediu a alternativa antes de
 * decidir, e o resultado corrige um detalhe do §6, item 19 do CLAUDE.md: com
 * {@code @EnableMethodSecurity}, o {@code AccessDeniedException} de um
 * {@code @PreAuthorize} <b>é</b> alcançável pelo {@code @ControllerAdvice} — ele é lançado
 * dentro do dispatch, então o {@code HandlerExceptionResolver} o vê antes do
 * {@code ExceptionTranslationFilter}, e um {@code @ExceptionHandler} devolve
 * {@code ErrorResponse} normalmente. O que continua valendo são os outros dois motivos, e
 * eles bastam:
 *
 * <ul>
 *   <li><b>Nenhum teste do projeto sobe contexto Spring</b>, então anotação de
 *       autorização ficaria sem cobertura nenhuma. Um {@code hasRole} escrito errado
 *       compila, passa na suíte inteira e abre a rota em silêncio.</li>
 *   <li>A autorização de {@code /api/users/**} tem lógica de dono e regional e
 *       <b>continua no serviço</b>. Anotar o resto criaria os dois lugares decidindo
 *       autorização que o S2a quis evitar — em vez de unificar, dobraria.</li>
 *   <li>O 403 do Spring traz {@code "Access Denied"}, em inglês e igual para todas as
 *       rotas. O projeto escreve mensagem em português e específica do recurso.</li>
 * </ul>
 *
 * <p><b>Escrita e consulta são o mesmo predicado hoje</b>, e mesmo assim são dois métodos:
 * a mensagem precisa dizer o que foi recusado, e a regra de leitura é a que tem chance de
 * afrouxar primeiro se um dia o colaborador precisar consultar a frota pela área logada.
 *
 * <p>Nada aqui protege o que é público: {@code GET /api/public/gas-stations/**} e
 * {@code POST /api/public/incident} passam pelos mesmos métodos de serviço e continuam
 * sem autorização, de propósito.
 */
@Component
public class AutorizacaoOperacional {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    public AutorizacaoOperacional(UsuarioAutenticadoProvider usuarioAutenticadoProvider) {
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
    }

    /** Criar, alterar ou excluir posto, veículo ou ocorrência. */
    public void autorizarEscrita() {
        exigirGestao(PERFIL_NAO_PERMITIDO_ESCRITA_MESSAGE);
    }

    /** Consultar veículo ou ocorrência pelas rotas autenticadas. */
    public void autorizarConsulta() {
        exigirGestao(PERFIL_NAO_PERMITIDO_CONSULTA_MESSAGE);
    }

    /**
     * O perfil vem da entidade, resolvida do banco pelo
     * {@link UsuarioAutenticadoProvider} — nunca do JWT. Mesmo motivo do §6, item 20:
     * o token vive até 30 dias para COLABORADOR, então alguém rebaixado de perfil
     * continuaria autorizado até o próximo login.
     */
    private void exigirGestao(String mensagem) {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();

        if (Perfil.ADMINISTRADOR.equals(autor.getPerfil())
                || Perfil.GESTOR_FROTA.equals(autor.getPerfil())) {
            return;
        }

        throw new PerfilNaoPermitidoException(mensagem);
    }
}
