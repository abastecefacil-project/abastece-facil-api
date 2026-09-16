package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.exception.EnvioEmailException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.github.api_abastecefacil.constants.EmailConstants.ENVIO_ACESSO_ENVIADO_LOG;
import static com.github.api_abastecefacil.constants.EmailConstants.ENVIO_ACESSO_FALHOU_LOG;
import static com.github.api_abastecefacil.constants.UserConstants.ROTA_DEFINIR_SENHA;
import static com.github.api_abastecefacil.constants.UserConstants.ROTA_REDEFINIR_SENHA;

/**
 * Emite um token de acesso e entrega o link correspondente por e-mail.
 *
 * <p>É a extração do que o convite de primeiro acesso (S2b1) e a recuperação de senha (S4)
 * fazem igual: gerar token, montar o link sobre {@code abastecefacil.email.frontend-url},
 * montar a mensagem e enviar. Até o S4 isso vivia em dois métodos privados do
 * {@code UserService}, e a recuperação precisaria da mesma sequência com outra finalidade —
 * copiá-la deixaria duas montagens de link e dois lugares lendo o prazo do token.
 *
 * <p><b>O que varia por finalidade está em dois {@code switch} sem {@code default}</b>
 * ({@link #rotaDe} e o prazo, delegado ao {@code TokenAcessoService}), como
 * {@code ConteudoEmail.de} e {@code AuthService.resolverExpiracao}. Uma finalidade nova
 * passa a quebrar a compilação aqui em vez de cair num caso silencioso e mandar a pessoa
 * para a tela errada.
 */
@Service
public class EnvioAcessoService {

    private static final Logger log = LoggerFactory.getLogger(EnvioAcessoService.class);

    private final TokenAcessoService tokenAcessoService;
    private final EnviadorEmail enviadorEmail;
    private final String frontendUrl;

    public EnvioAcessoService(
            TokenAcessoService tokenAcessoService,
            EnviadorEmail enviadorEmail,
            @Value("${abastecefacil.email.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.tokenAcessoService = tokenAcessoService;
        this.enviadorEmail = enviadorEmail;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Emite o token e envia o e-mail.
     *
     * <p><b>Não propaga falha de envio.</b> A {@code EnvioEmailException} vira um
     * {@code ERROR} no log e {@code false} no retorno, porque os dois chamadores precisam
     * continuar: o cadastro administrativo não pode desfazer um usuário já criado por
     * causa do provedor de e-mail (§6, item 24), e a recuperação de senha já respondeu ao
     * cliente antes de chegar aqui.
     *
     * <p>O token continua válido quando o envio falha — ele está no banco. O que faltou
     * foi a entrega, e o conserto é um novo envio, que invalida este.
     *
     * @return {@code true} se o e-mail saiu; {@code false} se o provedor recusou ou caiu.
     */
    public boolean enviar(User usuario, FinalidadeToken finalidade, String ipSolicitante) {
        String token = tokenAcessoService.gerarToken(usuario.getEmail(), finalidade, ipSolicitante);

        MensagemAcesso mensagem = new MensagemAcesso(
                usuario.getEmail(),
                usuario.getName(),
                montarLink(rotaDe(finalidade), token),
                finalidade,
                // O prazo exibido vem da mesma origem que calculou expira_em.
                tokenAcessoService.validadeHoras(finalidade));

        try {
            enviadorEmail.enviar(mensagem);
        } catch (EnvioEmailException e) {
            // Sem a URL: ela carrega o token em claro, e so o EnviadorEmailLog pode
            // registra-lo. A causa encadeada leva o diagnostico do provedor.
            log.error(ENVIO_ACESSO_FALHOU_LOG, usuario.getEmail(), finalidade, e);
            return false;
        }

        log.info(ENVIO_ACESSO_ENVIADO_LOG, usuario.getEmail(), finalidade);
        return true;
    }

    /**
     * Cada finalidade leva a uma tela diferente do frontend: quem está concluindo o
     * primeiro acesso e quem perdeu a senha chegam com contextos distintos.
     */
    private String rotaDe(FinalidadeToken finalidade) {
        return switch (finalidade) {
            case ATIVACAO -> ROTA_DEFINIR_SENHA;
            case RECUPERACAO -> ROTA_REDEFINIR_SENHA;
        };
    }

    /**
     * Escrever a base com barra final é natural em configuração, e concatenar direto
     * produziria {@code //definir-senha}. Alguns servidores tratam a barra dupla como
     * outra rota, e o link não abriria.
     */
    private String montarLink(String rota, String token) {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return base + rota + token;
    }
}
