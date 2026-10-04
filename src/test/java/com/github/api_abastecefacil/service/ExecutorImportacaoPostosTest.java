package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.ItemPlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.dto.gasStation.OcorrenciaPlanilha;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.exception.ImportacaoEmAndamentoException;
import com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException;
import com.github.api_abastecefacil.model.StatusImportacao;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Execução da importação de postos.
 *
 * <p><b>A thread dedicada é substituída por execução síncrona</b>: o mock da
 * {@code ExecucaoImportacaoPostos} roda a tarefa na hora, e quando o teste termina a
 * importação também terminou. O registro é o real, com Clock mockado, para o desfecho ser
 * conferido pelo que um chamador veria em {@code consultar}.
 *
 * <p>Strictness.LENIENT porque o relógio e a execução síncrona são stubados no setUp, e os
 * testes que não deixam a tarefa rodar não os consultam.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorImportacaoPostosTest {

    private static final Instant T0 = Instant.parse("2026-10-01T13:00:00Z");
    private static final Coordinates COORDENADAS = new Coordinates(new BigDecimal("-26.3"), new BigDecimal("-48.8"));

    @Mock
    private Clock clock;

    @Mock
    private ExecucaoImportacaoPostos execucao;

    @Mock
    private OpenStreetMapService openStreetMapService;

    @Mock
    private GravadorImportacaoPostos gravador;

    private RegistroImportacoesPostos registro;
    private ExecutorImportacaoPostos executor;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(T0);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        registro = new RegistroImportacoesPostos(clock);
        executor = novoExecutor(5);
        executarNaHora();
    }

    private ExecutorImportacaoPostos novoExecutor(int maxFalhas) {
        return new ExecutorImportacaoPostos(registro, execucao, openStreetMapService, gravador, maxFalhas);
    }

    private void executarNaHora() {
        doAnswer(invocacao -> {
            invocacao.<Runnable>getArgument(0).run();
            return null;
        }).when(execucao).submeter(any());
    }

    // ------------------------------------------------------------------ dados

    private static LinhaPlanilhaPosto linha(int numero) {
        return new LinhaPlanilhaPosto(numero, "1234567800019" + numero, "cnpj-" + numero, "Posto " + numero,
                null, null, "89201-250", "Rua " + numero + ", 10", "Centro", "Joinville", "SC", null, List.of());
    }

    private static ItemPlanoImportacao inserir(int numero) {
        return new ItemPlanoImportacao(null, "cnpj-" + numero, "Posto " + numero, null, "Joinville",
                List.of(), true, linha(numero));
    }

    private static ItemPlanoImportacao atualizar(long id, int numero, boolean requerGeocodificacao) {
        return new ItemPlanoImportacao(id, "cnpj-" + numero, "Posto " + numero, null, "Joinville",
                List.of(COLUNA_ENDERECO), requerGeocodificacao, linha(numero));
    }

    private static ItemPlanoImportacao desativar(long id) {
        return new ItemPlanoImportacao(id, "cnpj-d" + id, "Posto fechado", null, "Joinville", List.of(), false,
                null);
    }

    private static PlanoImportacao plano(List<ItemPlanoImportacao> inserir, List<ItemPlanoImportacao> atualizar,
                                         List<ItemPlanoImportacao> reativar, List<ItemPlanoImportacao> desativar) {
        return new PlanoImportacao(inserir, atualizar, reativar, desativar, 0, 0, 0, 0, List.of(), List.of());
    }

    private void geocodificacao(int numero, Optional<Coordinates> resultado) {
        when(openStreetMapService.geocodificarComFallback(eq("Rua " + numero + ", 10"), any(), any(), any(), any(), any()))
                .thenReturn(resultado);
    }

    private void falhaDeComunicacao(int numero) {
        when(openStreetMapService.geocodificarComFallback(eq("Rua " + numero + ", 10"), any(), any(), any(), any(), any()))
                .thenThrow(feignIndisponivel());
    }

    private static FeignException feignIndisponivel() {
        Request request = Request.create(Request.HttpMethod.GET, "https://nominatim.openstreetmap.org/search",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.ServiceUnavailable("Service Unavailable", request, null, null);
    }

    private ImportacaoPostosStatus executar(PlanoImportacao plano) {
        UUID id = executor.iniciar(plano);
        return executor.consultar(id);
    }

    // ------------------------------------------------------------------ entrada

    @Test
    void iniciar_ShouldReturnIdImmediately_WithTotalOfAllGroups_WhenTaskHasNotRunYet() {
        doAnswer(invocacao -> null).when(execucao).submeter(any());
        PlanoImportacao plano = plano(List.of(inserir(1), inserir(2)), List.of(atualizar(10, 3, false)),
                List.of(atualizar(11, 4, false)), List.of(desativar(12)));

        UUID id = executor.iniciar(plano);

        ImportacaoPostosStatus status = executor.consultar(id);
        assertThat(status.status()).isEqualTo(StatusImportacao.EM_ANDAMENTO);
        assertThat(status.total()).isEqualTo(5);
        assertThat(status.processados()).isZero();
        verifyNoInteractions(gravador, openStreetMapService);
    }

    @Test
    void iniciar_ShouldThrowImportacaoEmAndamento_WhenAnotherImportIsRunning() {
        doAnswer(invocacao -> null).when(execucao).submeter(any());
        PlanoImportacao plano = plano(List.of(inserir(1)), List.of(), List.of(), List.of());
        executor.iniciar(plano);

        assertThatThrownBy(() -> executor.iniciar(plano)).isInstanceOf(ImportacaoEmAndamentoException.class);
        verify(execucao, times(1)).submeter(any());
    }

    @Test
    void consultar_ShouldThrowImportacaoNaoEncontrada_WhenIdDoesNotExist() {
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> executor.consultar(inexistente))
                .isInstanceOf(ImportacaoNaoEncontradaException.class);
    }

    @Test
    void iniciar_ShouldMarkFailedAndReleaseTheSlot_WhenTheThreadRejectsTheTask() {
        doThrow(new TaskRejectedException("encerrado")).when(execucao).submeter(any());
        PlanoImportacao plano = plano(List.of(inserir(1)), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> executor.iniciar(plano)).isInstanceOf(TaskRejectedException.class);

        executarNaHora();
        geocodificacao(1, Optional.of(COORDENADAS));
        assertThat(executar(plano).status()).isEqualTo(StatusImportacao.CONCLUIDA);
    }

    // ------------------------------------------------------------------ ordem

    @Test
    void executar_ShouldProcessUpdatesWithoutGeocoding_ThenGeocodedItems_ThenDeactivations() {
        geocodificacao(1, Optional.of(COORDENADAS));
        geocodificacao(3, Optional.of(COORDENADAS));
        geocodificacao(5, Optional.of(COORDENADAS));
        // Nas listas, os que requerem geocodificação vêm antes, para provar a reordenação.
        PlanoImportacao plano = plano(
                List.of(inserir(1)),
                List.of(atualizar(30, 3, true), atualizar(20, 2, false)),
                List.of(atualizar(50, 5, true), atualizar(40, 4, false)),
                List.of(desativar(60)));

        ImportacaoPostosStatus status = executar(plano);

        InOrder ordem = inOrder(gravador);
        ordem.verify(gravador).atualizar(eq(20L), any(), eq(false));
        ordem.verify(gravador).atualizar(eq(40L), any(), eq(true));
        ordem.verify(gravador).inserir(any(), eq(COORDENADAS));
        ordem.verify(gravador).atualizarComCoordenadas(eq(30L), any(), eq(COORDENADAS), eq(false));
        ordem.verify(gravador).atualizarComCoordenadas(eq(50L), any(), eq(COORDENADAS), eq(true));
        ordem.verify(gravador).desativar(60L);

        assertThat(status.status()).isEqualTo(StatusImportacao.CONCLUIDA);
        assertThat(status.processados()).isEqualTo(6);
        assertThat(status.resumo().inseridos()).isEqualTo(1);
        assertThat(status.resumo().atualizados()).isEqualTo(2);
        assertThat(status.resumo().reativados()).isEqualTo(2);
        assertThat(status.resumo().desativados()).isEqualTo(1);
        assertThat(status.mensagem()).isEqualTo(IMPORTACAO_CONCLUIDA_MESSAGE);
    }

    @Test
    void executar_ShouldNotGeocode_UpdatesThatDoNotRequireIt() {
        PlanoImportacao plano = plano(List.of(), List.of(atualizar(20, 2, false)), List.of(), List.of());

        executar(plano);

        verifyNoInteractions(openStreetMapService);
    }

    @Test
    void executar_ShouldGeocodeWithStateAsUf() {
        geocodificacao(1, Optional.of(COORDENADAS));

        executar(plano(List.of(inserir(1)), List.of(), List.of(), List.of()));

        verify(openStreetMapService).geocodificarComFallback("Rua 1, 10", "Centro", "Joinville", "SC", "89201-250", "SC");
    }

    // ------------------------------------------------------------------ geocodificação vazia

    @Test
    void executar_ShouldNotInsertAndRecordError_WhenGeocodingIsEmpty() {
        geocodificacao(1, Optional.empty());

        ImportacaoPostosStatus status = executar(plano(List.of(inserir(1)), List.of(), List.of(), List.of()));

        verify(gravador, never()).inserir(any(), any());
        assertThat(status.status()).isEqualTo(StatusImportacao.CONCLUIDA);
        assertThat(status.resumo().inseridos()).isZero();
        assertThat(status.resumo().erros())
                .containsExactly(new OcorrenciaPlanilha(1, "cnpj-1", COORDENADAS_NAO_ENCONTRADAS_INSERIR_MESSAGE));
    }

    @Test
    void executar_ShouldUpdateWithoutAddressAndRecordWarning_WhenChangedAddressIsNotFound() {
        geocodificacao(3, Optional.empty());

        ImportacaoPostosStatus status = executar(plano(List.of(), List.of(atualizar(30, 3, true)), List.of(), List.of()));

        verify(gravador).atualizarSemEndereco(eq(30L), eq(linha(3)), eq(false));
        verify(gravador, never()).atualizarComCoordenadas(anyLong(), any(), any(), anyBoolean());
        verify(gravador, never()).atualizar(anyLong(), any(), anyBoolean());
        assertThat(status.resumo().atualizados()).isEqualTo(1);
        assertThat(status.resumo().avisos())
                .containsExactly(new OcorrenciaPlanilha(3, "cnpj-3", COORDENADAS_NAO_ENCONTRADAS_ATUALIZAR_MESSAGE));
        assertThat(status.resumo().erros()).isEmpty();
    }

    // ------------------------------------------------------------------ falhas de comunicação

    @Test
    void executar_ShouldReactivateWithoutAddress_RecordErrorAndCountTheFailure_WhenFeignFailsOnReactivation() {
        falhaDeComunicacao(5);
        executor = novoExecutor(1);

        ImportacaoPostosStatus status = executar(plano(List.of(), List.of(),
                List.of(atualizar(50, 5, true)), List.of(desativar(60))));

        verify(gravador).atualizarSemEndereco(eq(50L), eq(linha(5)), eq(true));
        assertThat(status.resumo().reativados()).isEqualTo(1);
        assertThat(status.resumo().erros())
                .containsExactly(new OcorrenciaPlanilha(5, "cnpj-5", FALHA_GEOCODIFICACAO_ATUALIZAR_MESSAGE));
        // Com limite 1, esta única falha basta para interromper: prova que ela foi contada.
        assertThat(status.status()).isEqualTo(StatusImportacao.FALHOU);
        verify(gravador, never()).desativar(anyLong());
    }

    @Test
    void executar_ShouldRecordErrorAndContinue_WhenFeignFailsOnASingleItem() {
        geocodificacao(1, Optional.of(COORDENADAS));
        falhaDeComunicacao(2);
        geocodificacao(3, Optional.of(COORDENADAS));

        ImportacaoPostosStatus status = executar(plano(List.of(inserir(1), inserir(2), inserir(3)),
                List.of(), List.of(), List.of(desativar(60))));

        verify(gravador).inserir(eq(linha(1)), any());
        verify(gravador, never()).inserir(eq(linha(2)), any());
        verify(gravador).inserir(eq(linha(3)), any());
        verify(gravador).desativar(60L);
        assertThat(status.status()).isEqualTo(StatusImportacao.CONCLUIDA);
        assertThat(status.resumo().inseridos()).isEqualTo(2);
        assertThat(status.resumo().erros())
                .containsExactly(new OcorrenciaPlanilha(2, "cnpj-2", FALHA_GEOCODIFICACAO_INSERIR_MESSAGE));
    }

    @Test
    void executar_ShouldStopAsFailedWithoutDeactivating_After5ConsecutiveFeignFailures() {
        List<ItemPlanoImportacao> itens = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            falhaDeComunicacao(i);
            itens.add(inserir(i));
        }

        ImportacaoPostosStatus status = executar(plano(itens, List.of(), List.of(), List.of(desativar(60))));

        verify(openStreetMapService, times(5)).geocodificarComFallback(any(), any(), any(), any(), any(), any());
        verify(gravador, never()).desativar(anyLong());
        verify(gravador, never()).inserir(any(), any());
        assertThat(status.status()).isEqualTo(StatusImportacao.FALHOU);
        assertThat(status.processados()).isEqualTo(5);
        assertThat(status.mensagem()).isEqualTo(String.format(IMPORTACAO_INTERROMPIDA_FALHAS_MESSAGE, 5, 5, 8));
        assertThat(status.resumo().erros()).hasSize(5);
    }

    @Test
    void executar_ShouldResetTheCounter_WhenAValidResponseComesBetweenFailures() {
        List<ItemPlanoImportacao> itens = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            if (i == 5) {
                geocodificacao(i, Optional.empty());
            } else {
                falhaDeComunicacao(i);
            }
            itens.add(inserir(i));
        }

        ImportacaoPostosStatus status = executar(plano(itens, List.of(), List.of(), List.of(desativar(60))));

        verify(openStreetMapService, times(9)).geocodificarComFallback(any(), any(), any(), any(), any(), any());
        verify(gravador).desativar(60L);
        assertThat(status.status()).isEqualTo(StatusImportacao.CONCLUIDA);
    }

    @Test
    void executar_ShouldFailWithoutDeactivating_WhenThrottleWaitIsInterrupted() {
        geocodificacao(1, Optional.of(COORDENADAS));
        when(openStreetMapService.geocodificarComFallback(eq("Rua 2, 10"), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("interrompida"));

        ImportacaoPostosStatus status = executar(plano(List.of(inserir(1), inserir(2), inserir(3)),
                List.of(), List.of(), List.of(desativar(60))));

        verify(gravador).inserir(eq(linha(1)), any());
        verify(gravador, never()).inserir(eq(linha(3)), any());
        verify(gravador, never()).desativar(anyLong());
        assertThat(status.status()).isEqualTo(StatusImportacao.FALHOU);
        assertThat(status.mensagem()).isEqualTo(String.format(IMPORTACAO_INTERROMPIDA_ERRO_MESSAGE, 2, 4));
        assertThat(status.resumo().inseridos()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ gravação

    @Test
    void executar_ShouldRecordErrorAndContinue_WhenSavingAnItemFails() {
        geocodificacao(1, Optional.of(COORDENADAS));
        geocodificacao(2, Optional.of(COORDENADAS));
        doThrow(new DataIntegrityViolationException("uk8atkmpnk417qqgkf1r1gw7ujk"))
                .when(gravador).inserir(eq(linha(1)), any());

        ImportacaoPostosStatus status = executar(plano(List.of(inserir(1), inserir(2)),
                List.of(), List.of(), List.of(desativar(60))));

        verify(gravador).inserir(eq(linha(2)), any());
        verify(gravador).desativar(60L);
        assertThat(status.status()).isEqualTo(StatusImportacao.CONCLUIDA);
        assertThat(status.resumo().inseridos()).isEqualTo(1);
        assertThat(status.resumo().erros())
                .containsExactly(new OcorrenciaPlanilha(1, "cnpj-1", CONFLITO_GRAVACAO_MESSAGE));
    }

    @Test
    void executar_ShouldRecordGenericError_WhenSavingFailsUnexpectedly() {
        doThrow(new RuntimeException("detalhe interno")).when(gravador).atualizar(eq(20L), any(), anyBoolean());

        ImportacaoPostosStatus status = executar(plano(List.of(), List.of(atualizar(20, 2, false)), List.of(), List.of()));

        assertThat(status.status()).isEqualTo(StatusImportacao.CONCLUIDA);
        assertThat(status.resumo().erros())
                .containsExactly(new OcorrenciaPlanilha(2, "cnpj-2", ERRO_GRAVACAO_MESSAGE));
    }

    @Test
    void executar_ShouldRecordDeactivationErrorWithLineZero() {
        doThrow(new RuntimeException("falhou")).when(gravador).desativar(60L);

        ImportacaoPostosStatus status = executar(plano(List.of(), List.of(), List.of(), List.of(desativar(60))));

        assertThat(status.resumo().desativados()).isZero();
        assertThat(status.resumo().erros())
                .containsExactly(new OcorrenciaPlanilha(LINHA_FORA_DA_PLANILHA, "cnpj-d60", ERRO_GRAVACAO_MESSAGE));
    }

    @Test
    void executar_ShouldDeactivateThroughTheWriter() {
        ImportacaoPostosStatus status = executar(plano(List.of(), List.of(), List.of(), List.of(desativar(60))));

        verify(gravador).desativar(60L);
        assertThat(status.resumo().desativados()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ resumo

    @Test
    void executar_ShouldMergePlanAndExecutionOccurrences_AndCarrySemAlteracao() {
        geocodificacao(1, Optional.empty());
        geocodificacao(3, Optional.empty());
        OcorrenciaPlanilha erroDoPlano = new OcorrenciaPlanilha(9, null, "CNPJ inválido");
        OcorrenciaPlanilha avisoDoPlano = new OcorrenciaPlanilha(8, "cnpj-8", "CNPJ duplicado");
        PlanoImportacao plano = new PlanoImportacao(List.of(inserir(1)), List.of(atualizar(30, 3, true)),
                List.of(), List.of(), 4, 20, 15, 10, List.of(erroDoPlano), List.of(avisoDoPlano));

        ImportacaoPostosStatus status = executar(plano);

        assertThat(status.resumo().semAlteracao()).isEqualTo(4);
        assertThat(status.resumo().erros()).containsExactly(erroDoPlano,
                new OcorrenciaPlanilha(1, "cnpj-1", COORDENADAS_NAO_ENCONTRADAS_INSERIR_MESSAGE));
        assertThat(status.resumo().avisos()).containsExactly(avisoDoPlano,
                new OcorrenciaPlanilha(3, "cnpj-3", COORDENADAS_NAO_ENCONTRADAS_ATUALIZAR_MESSAGE));
    }
}
