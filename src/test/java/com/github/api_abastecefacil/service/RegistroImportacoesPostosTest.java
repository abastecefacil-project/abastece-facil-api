package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.ResumoImportacaoPostos;
import com.github.api_abastecefacil.exception.ImportacaoEmAndamentoException;
import com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException;
import com.github.api_abastecefacil.model.StatusImportacao;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Registro em memória das importações de postos.
 *
 * <p>Clock mockado, no padrão do RateLimitServiceTest: a retenção de 24 horas é provada
 * avançando o relógio, sem esperar. Strictness.LENIENT porque o relógio é stubado no setUp e
 * nem todo teste o consulta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistroImportacoesPostosTest {

    private static final Instant T0 = Instant.parse("2026-10-01T13:00:00Z");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final ResumoImportacaoPostos RESUMO =
            new ResumoImportacaoPostos(1, 0, 0, 0, 0, List.of(), List.of());

    @Mock
    private Clock clock;

    private RegistroImportacoesPostos registro;

    @BeforeEach
    void setUp() {
        registro = new RegistroImportacoesPostos(clock);
        when(clock.getZone()).thenReturn(FUSO);
        avancarPara(T0);
    }

    private void avancarPara(Instant instante) {
        when(clock.instant()).thenReturn(instante);
    }

    @Test
    void iniciar_ShouldRegisterInProgressImport_WithTotalAndZeroProcessed() {
        ImportacaoPostosStatus status = registro.iniciar(7);

        assertThat(status.id()).isNotNull();
        assertThat(status.status()).isEqualTo(StatusImportacao.EM_ANDAMENTO);
        assertThat(status.total()).isEqualTo(7);
        assertThat(status.processados()).isZero();
        assertThat(status.iniciadaEm()).isEqualTo(LocalDateTime.ofInstant(T0, FUSO));
        assertThat(status.concluidaEm()).isNull();
        assertThat(status.resumo()).isNull();
        assertThat(registro.consultar(status.id())).isEqualTo(status);
    }

    @Test
    void iniciar_ShouldThrowImportacaoEmAndamento_WhenAnotherIsInProgress() {
        UUID primeira = registro.iniciar(3).id();

        assertThatThrownBy(() -> registro.iniciar(5))
                .isInstanceOf(ImportacaoEmAndamentoException.class)
                .hasMessageContaining(primeira.toString());
    }

    @Test
    void iniciar_ShouldAcceptNewImport_AfterThePreviousOneIsFinished() {
        UUID primeira = registro.iniciar(3).id();
        registro.finalizar(primeira, StatusImportacao.CONCLUIDA, "ok", RESUMO);

        ImportacaoPostosStatus segunda = registro.iniciar(5);

        assertThat(segunda.id()).isNotEqualTo(primeira);
        assertThat(registro.consultar(primeira).status()).isEqualTo(StatusImportacao.CONCLUIDA);
    }

    @Test
    void iniciar_ShouldLetExactlyOneThrough_WhenTwoThreadsStartAtTheSameTime() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = threads.submit(() -> tentarIniciar(largada));
            Future<Boolean> b = threads.submit(() -> tentarIniciar(largada));
            largada.countDown();

            List<Boolean> resultados = List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));

            assertThat(resultados).containsExactlyInAnyOrder(true, false);
        } finally {
            threads.shutdownNow();
        }
    }

    private boolean tentarIniciar(CountDownLatch largada) throws InterruptedException {
        largada.await();
        try {
            registro.iniciar(1);
            return true;
        } catch (ImportacaoEmAndamentoException e) {
            return false;
        }
    }

    @Test
    void avancar_ShouldIncrementProcessed() {
        UUID id = registro.iniciar(3).id();

        registro.avancar(id);
        registro.avancar(id);

        assertThat(registro.consultar(id).processados()).isEqualTo(2);
    }

    @Test
    void finalizar_ShouldRecordOutcomeMessageSummaryAndCompletionTime() {
        UUID id = registro.iniciar(3).id();
        avancarPara(T0.plusSeconds(90));

        registro.finalizar(id, StatusImportacao.FALHOU, "interrompida", RESUMO);

        ImportacaoPostosStatus status = registro.consultar(id);
        assertThat(status.status()).isEqualTo(StatusImportacao.FALHOU);
        assertThat(status.mensagem()).isEqualTo("interrompida");
        assertThat(status.resumo()).isEqualTo(RESUMO);
        assertThat(status.concluidaEm()).isEqualTo(LocalDateTime.ofInstant(T0.plusSeconds(90), FUSO));
    }

    @Test
    void consultar_ShouldThrowImportacaoNaoEncontrada_WhenIdDoesNotExist() {
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> registro.consultar(inexistente))
                .isInstanceOf(ImportacaoNaoEncontradaException.class);
    }

    @Test
    void consultar_ShouldDiscardImport_WhenFinishedMoreThan24HoursAgo() {
        UUID id = registro.iniciar(3).id();
        registro.finalizar(id, StatusImportacao.CONCLUIDA, "ok", RESUMO);

        avancarPara(T0.plus(Duration.ofHours(24)).plusSeconds(1));

        assertThatThrownBy(() -> registro.consultar(id))
                .isInstanceOf(ImportacaoNaoEncontradaException.class);
    }

    @Test
    void consultar_ShouldKeepImport_WhenFinishedExactly24HoursAgo() {
        UUID id = registro.iniciar(3).id();
        registro.finalizar(id, StatusImportacao.CONCLUIDA, "ok", RESUMO);

        avancarPara(T0.plus(Duration.ofHours(24)));

        assertThat(registro.consultar(id).status()).isEqualTo(StatusImportacao.CONCLUIDA);
    }

    @Test
    void consultar_ShouldNeverDiscardImportInProgress() {
        UUID id = registro.iniciar(3).id();

        avancarPara(T0.plus(Duration.ofDays(3)));

        assertThat(registro.consultar(id).status()).isEqualTo(StatusImportacao.EM_ANDAMENTO);
    }
}
