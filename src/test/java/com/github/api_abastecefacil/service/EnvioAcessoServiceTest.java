package com.github.api_abastecefacil.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.exception.EnvioEmailException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.github.api_abastecefacil.constants.UserConstants.ROTA_DEFINIR_SENHA;
import static com.github.api_abastecefacil.constants.UserConstants.ROTA_REDEFINIR_SENHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Cobre a extracao do S4: a emissao do token e o envio do link, compartilhados pelo
 * convite de primeiro acesso (S2b1) e pela recuperacao de senha (S4).
 *
 * <p>A montagem da mensagem, do link e do prazo era testada no UserServiceTest ate o S4 --
 * inclusive o teste de segredo do log. Migrou para ca junto com o codigo, em vez de sumir:
 * o UserService nao emite token nem envia e-mail mais.
 */
@ExtendWith(MockitoExtension.class)
class EnvioAcessoServiceTest {

    private static final String FRONTEND_URL = "https://app.abastecefacil.com.br";
    private static final long ATIVACAO_HORAS = 48L;
    private static final long RECUPERACAO_HORAS = 1L;
    private static final String IP = "10.0.0.7";
    private static final String TOKEN = "Zm9vYmFyLXRva2VuLWRlLXRlc3Rl";

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private EnviadorEmail enviadorEmail;

    private EnvioAcessoService envioAcessoService;

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    private User usuario;

    @BeforeEach
    void setUp() {
        // @InjectMocks nao serve: o construtor recebe a URL do frontend, que vem de
        // configuracao e e o proprio objeto de dois testes.
        envioAcessoService = new EnvioAcessoService(tokenAcessoService, enviadorEmail, FRONTEND_URL);

        usuario = new User().setId(5L).setName("Convidada").setEmail("convidada@fiesc.org.br")
                .setPerfil(Perfil.COLABORADOR).setActive(true).setSenhaDefinida(false);

        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EnvioAcessoService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private MensagemAcesso capturarEnviada() {
        ArgumentCaptor<MensagemAcesso> captor = ArgumentCaptor.forClass(MensagemAcesso.class);
        verify(enviadorEmail).enviar(captor.capture());
        return captor.getValue();
    }

    private List<String> mensagens() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ------------------------------------------------------------------ ativacao

    @Test
    void enviar_ShouldIssueTheTokenAndComposeTheMessage_ForAtivacao() {
        when(tokenAcessoService.gerarToken(usuario.getEmail(), FinalidadeToken.ATIVACAO, IP))
                .thenReturn(TOKEN);
        when(tokenAcessoService.validadeHoras(FinalidadeToken.ATIVACAO)).thenReturn(ATIVACAO_HORAS);

        envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP);

        MensagemAcesso enviada = capturarEnviada();
        assertThat(enviada.destinatario()).isEqualTo(usuario.getEmail());
        assertThat(enviada.nomeDestinatario()).isEqualTo(usuario.getName());
        assertThat(enviada.finalidade()).isEqualTo(FinalidadeToken.ATIVACAO);
        // A validade exibida tem que ser a configurada, nao uma constante do corpo -- e
        // desde o S4 vem da mesma origem que calculou expira_em.
        assertThat(enviada.validadeHoras()).isEqualTo(ATIVACAO_HORAS);
    }

    @Test
    void enviar_ShouldBuildTheLinkOverTheConfiguredFrontendUrl_ForAtivacao() {
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP);

        assertThat(capturarEnviada().urlAcao()).isEqualTo(FRONTEND_URL + ROTA_DEFINIR_SENHA + TOKEN);
    }

    // -------------------------------------------------------------- recuperacao

    @Test
    void enviar_ShouldIssueTheTokenAndComposeTheMessage_ForRecuperacao() {
        when(tokenAcessoService.gerarToken(usuario.getEmail(), FinalidadeToken.RECUPERACAO, IP))
                .thenReturn(TOKEN);
        when(tokenAcessoService.validadeHoras(FinalidadeToken.RECUPERACAO)).thenReturn(RECUPERACAO_HORAS);

        envioAcessoService.enviar(usuario, FinalidadeToken.RECUPERACAO, IP);

        MensagemAcesso enviada = capturarEnviada();
        assertThat(enviada.finalidade()).isEqualTo(FinalidadeToken.RECUPERACAO);
        assertThat(enviada.validadeHoras()).isEqualTo(RECUPERACAO_HORAS);
    }

    /**
     * As duas finalidades levam a telas diferentes do frontend. Trocar as rotas de lugar
     * mandaria quem perdeu a senha para a tela de primeiro acesso, e e um erro que so
     * apareceria em producao -- daí o teste explicito sobre a rota.
     */
    @Test
    void enviar_ShouldUseTheRecoveryRoute_ForRecuperacao() {
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        envioAcessoService.enviar(usuario, FinalidadeToken.RECUPERACAO, IP);

        assertThat(capturarEnviada().urlAcao()).isEqualTo(FRONTEND_URL + ROTA_REDEFINIR_SENHA + TOKEN);
    }

    @Test
    void enviar_ShouldUseADistinctRouteForEachFinalidade() {
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP);
        envioAcessoService.enviar(usuario, FinalidadeToken.RECUPERACAO, IP);

        ArgumentCaptor<MensagemAcesso> captor = ArgumentCaptor.forClass(MensagemAcesso.class);
        verify(enviadorEmail, times(2)).enviar(captor.capture());

        assertThat(captor.getAllValues().get(0).urlAcao())
                .isNotEqualTo(captor.getAllValues().get(1).urlAcao());
    }

    // ------------------------------------------------------------------- link

    @Test
    void enviar_ShouldNotDoubleTheSlash_WhenFrontendUrlEndsWithOne() {
        // Escrever a base com barra final e natural em configuracao.
        EnvioAcessoService comBarra =
                new EnvioAcessoService(tokenAcessoService, enviadorEmail, FRONTEND_URL + "/");
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        comBarra.enviar(usuario, FinalidadeToken.ATIVACAO, IP);

        assertThat(capturarEnviada().urlAcao()).doesNotContain("//definir-senha");
    }

    // ------------------------------------------------------------------ falha

    @Test
    void enviar_ShouldReturnTrue_WhenTheEmailGoesOut() {
        assertThat(envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP)).isTrue();
    }

    @Test
    void enviar_ShouldReturnFalseAndNotPropagate_WhenTheProviderFails() {
        // Falha de envio nunca derruba quem chamou: o cadastro nao pode desfazer um
        // usuario ja criado, e a recuperacao ja respondeu ao cliente.
        doThrow(new EnvioEmailException("provedor fora do ar")).when(enviadorEmail).enviar(any());

        assertThat(envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP)).isFalse();
    }

    @Test
    void enviar_ShouldLogAnError_WhenTheProviderFails() {
        // O ERROR e o que permite descobrir se a falha e sistematica: um gestor sozinho
        // nao distingue "o Resend caiu agora" de "a chave esta errada ha dois dias".
        doThrow(new EnvioEmailException("falhou")).when(enviadorEmail).enviar(any());

        envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP);

        assertThat(appender.list).anyMatch(e -> e.getLevel().equals(Level.ERROR));
    }

    @Test
    void enviar_ShouldNeverLogTheAccessLink() {
        // A URL carrega o token em claro. So o EnviadorEmailLog pode registra-la.
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);
        doThrow(new EnvioEmailException("falhou")).when(enviadorEmail).enviar(any());

        envioAcessoService.enviar(usuario, FinalidadeToken.ATIVACAO, IP);

        assertThat(appender.list).isNotEmpty();
        assertThat(mensagens()).noneMatch(m -> m.contains(TOKEN));
    }

    @Test
    void enviar_ShouldNeverLogTheAccessLink_OnSuccess() {
        when(tokenAcessoService.gerarToken(anyString(), any(), anyString())).thenReturn(TOKEN);

        envioAcessoService.enviar(usuario, FinalidadeToken.RECUPERACAO, IP);

        assertThat(mensagens()).noneMatch(m -> m.contains(TOKEN));
    }
}
