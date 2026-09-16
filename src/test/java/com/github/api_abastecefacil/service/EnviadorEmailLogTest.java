package com.github.api_abastecefacil.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.model.FinalidadeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnviadorEmailLogTest {

    private static final String DESTINATARIO = "colaborador@abastecefacil.com";
    private static final String NOME = "Pedro Alves";
    private static final String URL = "http://localhost:5173/definir-senha?token=Zm9vYmFy";

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    private final EnviadorEmailLog enviador = new EnviadorEmailLog();

    @BeforeEach
    void setUp() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EnviadorEmailLog.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private static MensagemAcesso mensagem(FinalidadeToken finalidade, long validadeHoras) {
        return new MensagemAcesso(DESTINATARIO, NOME, URL, finalidade, validadeHoras);
    }

    @Test
    void enviar_ShouldLogTheFullUrlIncludingTheToken() {
        // Ao contrario de todo o resto do projeto, aqui logar a URL e o comportamento
        // desejado: o log E o canal de entrega desta implementacao. Sem isso ela nao
        // teria funcao nenhuma em desenvolvimento.
        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        assertThat(mensagens()).anyMatch(m -> m.contains(URL));
    }

    @Test
    void enviar_ShouldLogRecipientFinalidadeAndSubject() {
        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        String linha = mensagens().get(0);
        assertThat(linha).contains(DESTINATARIO);
        assertThat(linha).contains("ATIVACAO");
        assertThat(linha).containsIgnoringCase("primeiro acesso");
    }

    @Test
    void enviar_ShouldLogADifferentSubject_ForEachFinalidade() {
        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));
        enviador.enviar(mensagem(FinalidadeToken.RECUPERACAO, 1));

        assertThat(mensagens()).hasSize(2);
        assertThat(mensagens().get(0)).isNotEqualTo(mensagens().get(1));
        assertThat(mensagens().get(1)).containsIgnoringCase("redefini");
    }

    @Test
    void enviar_ShouldLogTheValidityFromTheParameter() {
        enviador.enviar(mensagem(FinalidadeToken.RECUPERACAO, 1));

        assertThat(mensagens().get(0)).contains("validade=1");
    }

    @Test
    void enviar_ShouldNotLogTheEntireHtmlBody() {
        // O corpo tem dezenas de linhas de HTML e afogaria o console. O que interessa em
        // desenvolvimento e o link.
        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        assertThat(mensagens().get(0)).doesNotContain("<div");
    }

    @Test
    void enviar_ShouldLogAtInfoAndNeverAtError() {
        // Nao e falha: e o caminho normal em desenvolvimento.
        enviador.enviar(mensagem(FinalidadeToken.ATIVACAO, 48));

        assertThat(appender.list).allMatch(e -> e.getLevel().equals(Level.INFO));
    }

    private List<String> mensagens() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
