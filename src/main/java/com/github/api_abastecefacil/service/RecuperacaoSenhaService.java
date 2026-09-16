package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.github.api_abastecefacil.constants.UserConstants.RECUPERACAO_ENVIO_FALHOU_LOG;

/**
 * Resolve o que fazer com um pedido de recuperação de senha, <b>depois</b> de a resposta
 * já ter sido devolvida ao cliente.
 *
 * <p><b>É aqui que mora toda a ramificação do fluxo, e essa é a decisão central do S4.</b>
 * O requisito é que e-mail inexistente, usuário inativo, usuário sem senha definida e
 * usuário comum sejam indistinguíveis pela resposta, pelo status <i>e pelo tempo</i>.
 * Concentrar as decisões neste ponto assíncrono torna a propriedade <b>estrutural</b>: o
 * {@code AuthService} não consulta o usuário, não tem {@code if} nenhum sobre ele e
 * responde antes que qualquer I/O comece. Não há tempo a vazar porque não há trabalho
 * acontecendo antes da resposta — e não é preciso calibrar atraso artificial nenhum.
 *
 * <p><b>{@code @Async} depende de {@code @EnableAsync}</b>, em {@code SchedulingConfig}.
 * Sem a habilitação a anotação é ignorada em silêncio e este método passa a rodar na
 * thread da requisição — a aplicação sobe, nada falha, e a garantia de tempo constante
 * desaparece sem aviso. É a mesma armadilha que o {@code @Scheduled} tem desde o M2.
 *
 * <p>Sem {@code @Transactional}: {@code gerarToken} já abre a sua própria transação, na
 * thread do executor.
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private final UserRepository userRepository;
    private final EnvioAcessoService envioAcessoService;

    public RecuperacaoSenhaService(UserRepository userRepository, EnvioAcessoService envioAcessoService) {
        this.userRepository = userRepository;
        this.envioAcessoService = envioAcessoService;
    }

    /**
     * Envia o link de acesso, se houver a quem enviar.
     *
     * <p>E-mail sem conta e conta inativa terminam aqui em silêncio: não há nada a
     * entregar, e qualquer sinal — resposta, log de nível visível ao cliente, atraso —
     * seria a confirmação de existência que o fluxo inteiro evita.
     *
     * <p><b>Nada escapa deste método.</b> O {@code catch} abrangente é deliberado: um
     * {@code @Async void} não tem para quem propagar, a resposta HTTP já foi enviada, e uma
     * exceção solta terminaria num handler de executor que ninguém lê. O
     * {@code EnvioAcessoService} já trata a falha do provedor de e-mail; o que este bloco
     * cobre é o resto — indisponibilidade do banco ao emitir o token, por exemplo.
     */
    @Async
    public void enviarLink(String email, String ipSolicitante) {
        try {
            userRepository.findByEmail(email)
                    .filter(usuario -> Boolean.TRUE.equals(usuario.getActive()))
                    .ifPresent(usuario -> envioAcessoService.enviar(usuario, finalidadeDe(usuario), ipSolicitante));
        } catch (RuntimeException e) {
            // O e-mail entra no log; o token e a URL, nunca -- e nao ha token ainda se a
            // falha foi na emissao.
            log.error(RECUPERACAO_ENVIO_FALHOU_LOG, email, e);
        }
    }

    /**
     * Quem nunca definiu senha recebe o convite de ativação, não o de recuperação.
     *
     * <p>Não há senha a recuperar: a pessoa provavelmente perdeu o convite de primeiro
     * acesso, e do ponto de vista dela o resultado esperado é o mesmo — um e-mail com um
     * link que a leva a escolher uma senha. Mandar um link de recuperação a levaria para a
     * tela errada, e recusar o pedido a deixaria sem saída, já que o reenvio de convite
     * (§6, item 25) exige um gestor autenticado.
     *
     * <p>Reaproveita o mesmo mecanismo de convite do S2b1, com a validade mais longa da
     * ativação — o e-mail anuncia o prazo real, seja qual for.
     */
    private FinalidadeToken finalidadeDe(User usuario) {
        return Boolean.TRUE.equals(usuario.getSenhaDefinida())
                ? FinalidadeToken.RECUPERACAO
                : FinalidadeToken.ATIVACAO;
    }
}
