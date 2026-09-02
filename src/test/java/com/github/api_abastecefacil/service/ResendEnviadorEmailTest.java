package com.github.api_abastecefacil.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.exception.EnvioEmailException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static com.github.api_abastecefacil.constants.EmailConstants.ENVIO_FALHOU_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Exercita o adaptador sem rede e sem contexto Spring.
 *
 * <p>{@code MockRestServiceServer.bindTo(RestClient.Builder)} troca a fabrica de
 * requisicoes do builder antes de o RestClient ser construido -- e por isso que
 * ResendEnviadorEmail recebe o builder no construtor em vez de criar o seu proprio.
 */
class ResendEnviadorEmailTest {

    private static final String API_URL = "https://api.resend.com";
    private static final String API_KEY = "re_ChaveDeTeste_NaoUsarEmProducao";
    private static final String REMETENTE = "Abastece Fácil <onboarding@resend.dev>";

    private static final String DESTINATARIO = "colaborador@abastecefacil.com";
    private static final String URL_ACAO = "https://app.abastecefacil.com.br/definir-senha?token=SEGREDO";

    private static final String CORPO_SUCESSO = "{\"id\":\"8f1c-4b2a\"}";

    private MockRestServiceServer server;
    private ResendEnviadorEmail enviador;

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        enviador = new ResendEnviadorEmail(builder, API_URL, API_KEY, REMETENTE);

        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ResendEnviadorEmail.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private static MensagemAcesso mensagem(FinalidadeToken finalidade, long validadeHoras) {
        return new MensagemAcesso(DESTINATARIO, "Pedro Alves", URL_ACAO, finalidade, validadeHoras);
    }

    @Test
    void enviar_ShouldPostToTheEmailsEndpoint_WithBearerAuthorization() {
        server.expect(requestTo(API_URL + "/emails"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(CORPO_SUCESSO, MediaType.APPLICATION_JSON));

        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        server.verify();
    }

    @Test
    void enviar_ShouldSendFromToSubjectHtmlAndText_InTheBody() {
        server.expect(requestTo(API_URL + "/emails"))
                .andExpect(jsonPath("$.from").value(REMETENTE))
                .andExpect(jsonPath("$.to[0]").value(DESTINATARIO))
                .andExpect(jsonPath("$.subject").exists())
                .andExpect(jsonPath("$.html").exists())
                .andExpect(jsonPath("$.text").exists())
                .andRespond(withSuccess(CORPO_SUCESSO, MediaType.APPLICATION_JSON));

        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        server.verify();
    }

    @Test
    void enviar_ShouldThrowEnvioEmailException_WhenApiKeyIsInvalid() {
        // O caso real: chave errada em ABASTECEFACIL_EMAIL_API_KEY. Tem que virar erro de
        // negocio tratavel, nunca HttpClientErrorException crua -- o GlobalExceptionHandler
        // nao tem fallback, entao ela viraria um 500 sem ErrorResponse.
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"message\":\"API key is invalid\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        EnvioEmailException ex = assertThrows(EnvioEmailException.class,
                () -> enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48)));

        assertThat(ex.getMessage()).isEqualTo(ENVIO_FALHOU_MESSAGE);
    }

    @Test
    void enviar_ShouldThrowEnvioEmailException_WhenProviderReturnsServerError() {
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withServerError());

        assertThrows(EnvioEmailException.class,
                () -> enviador.enviar(mensagem(FinalidadeToken.RECUPERACAO, 1)));
    }

    @Test
    void enviar_ShouldThrowEnvioEmailException_WhenTheProviderIsUnreachable() {
        // Falha de transporte: DNS, timeout, conexao recusada.
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withException(new IOException("conexão recusada")));

        EnvioEmailException ex = assertThrows(EnvioEmailException.class,
                () -> enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48)));

        assertThat(ex.getMessage()).isEqualTo(ENVIO_FALHOU_MESSAGE);
        // A causa e encadeada para o stack trace do log, mas nao entra na mensagem.
        assertThat(ex.getCause()).isNotNull();
    }

    @Test
    void enviar_ShouldNeverLogTheApiKey() {
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(EnvioEmailException.class,
                () -> enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48)));

        assertThat(mensagens()).noneMatch(m -> m.contains(API_KEY));
    }

    @Test
    void enviar_ShouldNeverLogTheUrlWithTheToken_OnSuccessOrOnFailure() {
        // A URL carrega o token em claro. So EnviadorEmailLog pode registra-la, e ele
        // existe apenas para desenvolvimento.
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withSuccess(CORPO_SUCESSO, MediaType.APPLICATION_JSON));
        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        server.reset();
        server.expect(requestTo(API_URL + "/emails")).andRespond(withServerError());
        assertThrows(EnvioEmailException.class,
                () -> enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48)));

        assertThat(mensagens()).noneMatch(m -> m.contains(URL_ACAO));
        assertThat(mensagens()).noneMatch(m -> m.contains("SEGREDO"));
    }

    @Test
    void enviar_ShouldNotLogTheResponseBody_WhenTheProviderRejects() {
        // O corpo pode ecoar remetente ou outro detalhe de configuracao. O status basta
        // para orientar o diagnostico.
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"message\":\"domain not verified: interno.fiesc.com.br\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(EnvioEmailException.class,
                () -> enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48)));

        assertThat(mensagens()).noneMatch(m -> m.contains("interno.fiesc.com.br"));
        assertThat(mensagens()).anyMatch(m -> m.contains("422"));
    }

    @Test
    void enviar_ShouldLogTheResendMessageId_OnSuccess() {
        server.expect(requestTo(API_URL + "/emails"))
                .andRespond(withSuccess(CORPO_SUCESSO, MediaType.APPLICATION_JSON));

        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        assertThat(mensagens()).anyMatch(m -> m.contains("8f1c-4b2a"));
    }

    private List<String> mensagens() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
