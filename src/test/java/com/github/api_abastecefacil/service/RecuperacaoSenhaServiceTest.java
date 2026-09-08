package com.github.api_abastecefacil.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * O lado assincrono do S4: tudo o que decide o que fazer com um pedido de recuperacao,
 * depois de a resposta generica ja ter sido devolvida.
 *
 * <p>O @Async e inerte aqui -- sem contexto Spring nao ha proxy --, entao o metodo roda
 * na thread do teste. E o suficiente: o que se testa e a decisao, nao o agendamento.
 */
@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final String EMAIL = "colaborador@fiesc.org.br";
    private static final String IP = "10.0.0.7";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EnvioAcessoService envioAcessoService;

    @InjectMocks
    private RecuperacaoSenhaService recuperacaoSenhaService;

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RecuperacaoSenhaService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private User usuario(boolean ativo, boolean senhaDefinida) {
        return new User().setId(5L).setName("Mariana Prado").setEmail(EMAIL)
                .setPerfil(Perfil.COLABORADOR).setActive(ativo).setSenhaDefinida(senhaDefinida);
    }

    @Test
    void enviarLink_ShouldSendRecuperacao_WhenTheUserAlreadyHasAPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(true, true)));

        recuperacaoSenhaService.enviarLink(EMAIL, IP);

        verify(envioAcessoService).enviar(any(User.class), eq(FinalidadeToken.RECUPERACAO), eq(IP));
    }

    /**
     * Criterio de aceite obrigatorio do S4: quem nunca definiu a senha recebe o e-mail de
     * ATIVACAO, nao o de recuperacao.
     *
     * <p>Nao ha senha a recuperar -- a pessoa perdeu o convite de primeiro acesso. Do
     * ponto de vista dela o desfecho esperado e o mesmo, um link que a leva a escolher uma
     * senha; o que muda e a tela para onde o link aponta.
     */
    @Test
    void enviarLink_ShouldSendAtivacao_WhenTheUserNeverSetAPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(true, false)));

        recuperacaoSenhaService.enviarLink(EMAIL, IP);

        verify(envioAcessoService).enviar(any(User.class), eq(FinalidadeToken.ATIVACAO), eq(IP));
        verify(envioAcessoService, never()).enviar(any(), eq(FinalidadeToken.RECUPERACAO), anyString());
    }

    @Test
    void enviarLink_ShouldDoNothing_WhenTheEmailIsUnknown() {
        when(userRepository.findByEmail("ninguem@fiesc.org.br")).thenReturn(Optional.empty());

        recuperacaoSenhaService.enviarLink("ninguem@fiesc.org.br", IP);

        verifyNoInteractions(envioAcessoService);
    }

    @Test
    void enviarLink_ShouldDoNothing_WhenTheUserIsInactive() {
        // Conta desativada nao recebe link: ela nao autentica desde o S3, e enviar
        // confirmaria a existencia da conta a quem digitou o endereco.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(false, true)));

        recuperacaoSenhaService.enviarLink(EMAIL, IP);

        verifyNoInteractions(envioAcessoService);
    }

    @Test
    void enviarLink_ShouldNotPropagate_WhenSomethingFails() {
        // @Async void nao tem para quem propagar: a resposta HTTP ja foi enviada, e uma
        // excecao solta terminaria num handler de executor que ninguem le.
        when(userRepository.findByEmail(EMAIL)).thenThrow(new IllegalStateException("banco fora do ar"));

        assertThatCode(() -> recuperacaoSenhaService.enviarLink(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void enviarLink_ShouldLogAnError_WhenSomethingFails() {
        // O log e o unico canal deste erro: o solicitante ja recebeu 200.
        when(userRepository.findByEmail(EMAIL)).thenThrow(new IllegalStateException("banco fora do ar"));

        recuperacaoSenhaService.enviarLink(EMAIL, IP);

        assertThat(appender.list).anyMatch(e -> e.getLevel().equals(Level.ERROR));
    }

    @Test
    void enviarLink_ShouldNotLogAnything_WhenTheEmailIsUnknown() {
        // Silencio completo para e-mail inexistente: nem sucesso, nem erro.
        when(userRepository.findByEmail("ninguem@fiesc.org.br")).thenReturn(Optional.empty());

        recuperacaoSenhaService.enviarLink("ninguem@fiesc.org.br", IP);

        assertThat(appender.list).isEmpty();
    }
}
