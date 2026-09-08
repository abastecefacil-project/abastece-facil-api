package com.github.api_abastecefacil.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static com.github.api_abastecefacil.constants.RateLimitConstants.JANELA_MAXIMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Limitador de solicitacoes do S4.
 *
 * <p><b>O Clock mockado e o que torna estes testes possiveis.</b> Com Instant.now() fixo
 * no servico, provar que a janela de quinze minutos desliza exigiria um teste que dorme
 * quinze minutos. Aqui o relogio e avancado artificialmente com avancarPara(...), e a
 * mesma logica e exercitada em milissegundos.
 *
 * <p>Strictness.LENIENT porque o relogio e stubado uma vez no setUp e nem todo teste
 * consulta o tempo o mesmo numero de vezes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-08T10:00:00Z");

    private static final String CHAVE = "recuperacao:email:colaborador@fiesc.org.br";
    private static final int LIMITE = 3;
    private static final Duration JANELA = Duration.ofMinutes(15);

    @Mock
    private Clock clock;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(clock);
        avancarPara(T0);
    }

    private void avancarPara(Instant instante) {
        when(clock.instant()).thenReturn(instante);
    }

    /** Registra n solicitacoes no instante corrente do relogio. */
    private void solicitar(int quantas) {
        for (int i = 0; i < quantas; i++) {
            rateLimitService.registrar(CHAVE);
        }
    }

    @Test
    void excedeu_ShouldReturnFalse_ForAKeyNeverSeen() {
        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isFalse();
    }

    @Test
    void excedeu_ShouldReturnFalse_BelowTheLimit() {
        solicitar(2);

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isFalse();
    }

    /**
     * O criterio de aceite: a quarta solicitacao para o mesmo e-mail dentro de quinze
     * minutos e recusada. As tres primeiras passam.
     */
    @Test
    void excedeu_ShouldReturnTrue_OnTheFourthRequestWithinTheWindow() {
        solicitar(3);

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isTrue();
    }

    @Test
    void excedeu_ShouldSlideTheWindow_AfterTheDurationHasPassed() {
        solicitar(3);
        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isTrue();

        // Um segundo depois de a janela fechar sobre as tres primeiras.
        avancarPara(T0.plus(JANELA).plusSeconds(1));

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isFalse();
    }

    @Test
    void excedeu_ShouldStillBlock_JustBeforeTheWindowCloses() {
        solicitar(3);

        avancarPara(T0.plus(JANELA).minusSeconds(1));

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isTrue();
    }

    @Test
    void excedeu_ShouldSlidePartially_WhenOnlySomeRequestsFellOutOfTheWindow() {
        solicitar(2);

        // Duas envelhecem, uma nova entra: uma dentro da janela, entao ainda ha espaco.
        avancarPara(T0.plus(JANELA).plusSeconds(1));
        solicitar(1);

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isFalse();
    }

    @Test
    void excedeu_ShouldNotMixDistinctKeys() {
        solicitar(3);

        assertThat(rateLimitService.excedeu("recuperacao:ip:10.0.0.7", LIMITE, JANELA)).isFalse();
    }

    @Test
    void excedeu_ShouldRespectTheLimitGivenByTheCaller() {
        // A politica e do chamador: o mesmo servico atende o limite por e-mail e o por IP.
        solicitar(3);

        assertThat(rateLimitService.excedeu(CHAVE, 10, JANELA)).isFalse();
    }

    @Test
    void excedeu_ShouldNotCountAsARequest() {
        // Consultar e registrar sao separados: quem aplica duas regras precisa conferir as
        // duas antes de gravar qualquer uma.
        solicitar(2);

        rateLimitService.excedeu(CHAVE, LIMITE, JANELA);
        rateLimitService.excedeu(CHAVE, LIMITE, JANELA);

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isFalse();
    }

    // ------------------------------------------------------------------ limpeza

    @Test
    void limpar_ShouldDropKeysWithNoRecentRequests() {
        solicitar(3);

        avancarPara(T0.plus(JANELA_MAXIMA).plusSeconds(1));
        rateLimitService.limpar();

        // A chave sumiu do mapa; qualquer limite volta a estar livre.
        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isFalse();
    }

    @Test
    void limpar_ShouldKeepKeysStillInsideTheWindow() {
        solicitar(3);

        rateLimitService.limpar();

        assertThat(rateLimitService.excedeu(CHAVE, LIMITE, JANELA)).isTrue();
    }
}
