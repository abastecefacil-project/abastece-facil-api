package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.ItemPlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.PreviaImportacaoResponse;
import com.github.api_abastecefacil.dto.gasStation.ResultadoLeituraPlanilha;
import com.github.api_abastecefacil.exception.ImportacaoNaoEmAndamentoException;
import com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException;
import com.github.api_abastecefacil.exception.PerfilNaoPermitidoException;
import com.github.api_abastecefacil.exception.PlanilhaInvalidaException;
import com.github.api_abastecefacil.exception.PlanilhaSemPostosNoEscopoException;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.StatusImportacao;
import com.github.api_abastecefacil.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.ARQUIVO_ILEGIVEL_MESSAGE;
import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.ARQUIVO_VAZIO_MESSAGE;
import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.PLANILHA_INVALIDA_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fachada da importação de postos. A regra de ADMINISTRADOR é provada no
 * AutorizacaoOperacionalTest; aqui se prova que a guarda é <b>chamada</b>, e antes de tudo —
 * inclusive antes de ler o arquivo, por isso o MultipartFile mockado entra no
 * verifyNoInteractions.
 */
@ExtendWith(MockitoExtension.class)
class ImportacaoPostosServiceTest {

    private static final ResultadoLeituraPlanilha LEITURA =
            new ResultadoLeituraPlanilha(10, 8, List.of(), List.of(), List.of());

    @Mock
    private AutorizacaoOperacional autorizacaoOperacional;

    @Mock
    private LeitorPlanilhaPostos leitor;

    @Mock
    private PlanejadorImportacaoPostos planejador;

    @Mock
    private ExecutorImportacaoPostos executor;

    @Mock
    private RegistroImportacoesPostos registro;

    private ImportacaoPostosService service;

    @BeforeEach
    void setUp() {
        service = new ImportacaoPostosService(autorizacaoOperacional, leitor, planejador, executor, registro);
    }

    private static MockMultipartFile planilha(String nome) {
        return new MockMultipartFile("arquivo", nome, "application/octet-stream", new byte[]{1, 2, 3});
    }

    private static PlanoImportacao plano() {
        ItemPlanoImportacao item = new ItemPlanoImportacao(null, "12.345.678/0001-95", "Posto", "Posto", "Joinville",
                List.of(), true, null);
        return new PlanoImportacao(List.of(item), List.of(), List.of(), List.of(), 2, 10, 8, 5, List.of(), List.of());
    }

    private void negarAcesso() {
        doThrow(new PerfilNaoPermitidoException("negado")).when(autorizacaoOperacional).autorizarAdministracao();
    }

    // ------------------------------------------------- autorização antes de tudo

    @Test
    void previa_ShouldAuthorizeBeforeTouchingAnythingElse() {
        negarAcesso();
        MultipartFile arquivo = mock(MultipartFile.class);

        assertThrows(PerfilNaoPermitidoException.class, () -> service.previa(arquivo));

        verifyNoInteractions(leitor, planejador, executor, registro, arquivo);
    }

    @Test
    void iniciar_ShouldAuthorizeBeforeTouchingAnythingElse() {
        negarAcesso();
        MultipartFile arquivo = mock(MultipartFile.class);

        assertThrows(PerfilNaoPermitidoException.class, () -> service.iniciar(arquivo));

        verifyNoInteractions(leitor, planejador, executor, registro, arquivo);
    }

    @Test
    void consultar_ShouldAuthorizeBeforeTouchingAnythingElse() {
        negarAcesso();

        assertThrows(PerfilNaoPermitidoException.class, () -> service.consultar(UUID.randomUUID().toString()));

        verifyNoInteractions(leitor, planejador, executor, registro);
    }

    @Test
    void cancelar_ShouldAuthorizeBeforeTouchingAnythingElse() {
        negarAcesso();

        assertThrows(PerfilNaoPermitidoException.class, () -> service.cancelar(UUID.randomUUID().toString()));

        verifyNoInteractions(leitor, planejador, executor, registro);
    }

    @Test
    void atual_ShouldAuthorizeBeforeTouchingAnythingElse() {
        negarAcesso();

        assertThrows(PerfilNaoPermitidoException.class, () -> service.atual());

        verifyNoInteractions(leitor, planejador, executor, registro);
    }

    // ------------------------------------------------------------------ prévia

    @Test
    void previa_ShouldReadPlanAndConvert() {
        when(leitor.ler(any())).thenReturn(LEITURA);
        when(planejador.planejar(LEITURA)).thenReturn(plano());

        PreviaImportacaoResponse previa = service.previa(planilha("postos.xlsx"));

        assertThat(previa.semAlteracao()).isEqualTo(2);
        assertThat(previa.inserir()).hasSize(1);
        verifyNoInteractions(executor, registro);
    }

    // ------------------------------------------------------------------ iniciar

    @Test
    void iniciar_ShouldPlanFromTheUploadedFileAndStartTheExecution() {
        PlanoImportacao plano = plano();
        UUID id = UUID.randomUUID();
        when(leitor.ler(any())).thenReturn(LEITURA);
        when(planejador.planejar(LEITURA)).thenReturn(plano);
        when(executor.iniciar(plano)).thenReturn(id);

        assertThat(service.iniciar(planilha("postos.xlsx"))).isEqualTo(id);
    }

    @Test
    void iniciar_ShouldPropagatePlanningErrorSynchronously_WithoutStartingTheExecution() {
        when(leitor.ler(any())).thenReturn(LEITURA);
        when(planejador.planejar(LEITURA)).thenThrow(new PlanilhaSemPostosNoEscopoException("nenhuma linha"));

        assertThrows(PlanilhaSemPostosNoEscopoException.class, () -> service.iniciar(planilha("postos.xlsx")));

        verifyNoInteractions(executor);
    }

    @Test
    void iniciar_ShouldPropagateReadingErrorSynchronously_WithoutPlanning() {
        when(leitor.ler(any())).thenThrow(new PlanilhaInvalidaException("cabeçalho"));

        assertThrows(PlanilhaInvalidaException.class, () -> service.iniciar(planilha("postos.xlsx")));

        verifyNoInteractions(planejador, executor);
    }

    // ------------------------------------------------------------------ arquivo

    @Test
    void previa_ShouldRejectEmptyFile_WithoutReading() {
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "postos.xlsx", "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> service.previa(vazio))
                .isInstanceOf(PlanilhaInvalidaException.class)
                .hasMessage(ARQUIVO_VAZIO_MESSAGE);
        verifyNoInteractions(leitor, planejador);
    }

    @Test
    void previa_ShouldRejectFileWithoutXlsxExtension_WithoutReading() {
        assertThatThrownBy(() -> service.previa(planilha("postos.xls")))
                .isInstanceOf(PlanilhaInvalidaException.class)
                .hasMessage(PLANILHA_INVALIDA_MESSAGE);
        verifyNoInteractions(leitor, planejador);
    }

    @Test
    void iniciar_ShouldRejectFileWithoutName_WithoutReading() {
        MockMultipartFile semNome = new MockMultipartFile("arquivo", null, "application/octet-stream", new byte[]{1});

        assertThrows(PlanilhaInvalidaException.class, () -> service.iniciar(semNome));
        verifyNoInteractions(leitor, planejador, executor);
    }

    @Test
    void previa_ShouldAcceptXlsxExtensionInUpperCase() {
        when(leitor.ler(any())).thenReturn(LEITURA);
        when(planejador.planejar(LEITURA)).thenReturn(plano());

        assertThat(service.previa(planilha("POSTOS.XLSX"))).isNotNull();
    }

    @Test
    void previa_ShouldTranslateIOExceptionIntoPlanilhaInvalida() throws IOException {
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getOriginalFilename()).thenReturn("postos.xlsx");
        IOException falha = new IOException("disco");
        when(arquivo.getInputStream()).thenThrow(falha);

        assertThatThrownBy(() -> service.previa(arquivo))
                .isInstanceOf(PlanilhaInvalidaException.class)
                .hasMessage(ARQUIVO_ILEGIVEL_MESSAGE)
                .hasCause(falha);
    }

    @Test
    void previa_ShouldCloseTheUploadStream() throws IOException {
        InputStream conteudo = mock(InputStream.class);
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getOriginalFilename()).thenReturn("postos.xlsx");
        when(arquivo.getInputStream()).thenReturn(conteudo);
        when(leitor.ler(conteudo)).thenReturn(LEITURA);
        when(planejador.planejar(LEITURA)).thenReturn(plano());

        service.previa(arquivo);

        verify(conteudo).close();
    }

    // ------------------------------------------------------------------ consulta

    @Test
    void consultar_ShouldThrowImportacaoNaoEncontrada_WhenIdIsNotAUuid() {
        assertThrows(ImportacaoNaoEncontradaException.class, () -> service.consultar("nao-e-uuid"));

        verify(executor, never()).consultar(any());
    }

    @Test
    void consultar_ShouldDelegate_WhenIdIsAValidUuid() {
        UUID id = UUID.randomUUID();
        ImportacaoPostosStatus status = new ImportacaoPostosStatus(id, StatusImportacao.EM_ANDAMENTO, 3, 1,
                LocalDateTime.now(), null, null, null);
        when(executor.consultar(id)).thenReturn(status);

        assertThat(service.consultar(id.toString())).isEqualTo(status);
    }

    @Test
    void atual_ShouldReturnEmpty_WhenNoImportIsRunning() {
        when(registro.atual()).thenReturn(Optional.empty());

        assertThat(service.atual()).isEmpty();
    }

    @Test
    void atual_ShouldReturnTheRunningImport() {
        ImportacaoPostosStatus status = new ImportacaoPostosStatus(UUID.randomUUID(), StatusImportacao.EM_ANDAMENTO,
                3, 1, LocalDateTime.now(), null, null, null);
        when(registro.atual()).thenReturn(Optional.of(status));

        assertThat(service.atual()).contains(status);
    }

    // ------------------------------------------------------------------ cancelamento

    private void autenticadoComoAdministrador() {
        when(autorizacaoOperacional.autorizarAdministracao())
                .thenReturn(new User().setId(1L).setEmail("admin@fiesc.org.br").setPerfil(Perfil.ADMINISTRADOR));
    }

    @Test
    void cancelar_ShouldRegisterTheRequestWithTheAuthorEmail_AndReturnTheCurrentStatus() {
        autenticadoComoAdministrador();
        UUID id = UUID.randomUUID();
        ImportacaoPostosStatus status = new ImportacaoPostosStatus(id, StatusImportacao.EM_ANDAMENTO, 10, 4,
                LocalDateTime.now(), null, null, null);
        when(registro.solicitarCancelamento(id, "admin@fiesc.org.br")).thenReturn(status);

        assertThat(service.cancelar(id.toString())).isEqualTo(status);
        verifyNoInteractions(executor);
    }

    @Test
    void cancelar_ShouldThrowImportacaoNaoEncontrada_WhenIdIsNotAUuid() {
        autenticadoComoAdministrador();

        assertThrows(ImportacaoNaoEncontradaException.class, () -> service.cancelar("nao-e-uuid"));

        verifyNoInteractions(registro);
    }

    @Test
    void cancelar_ShouldPropagateImportacaoNaoEmAndamento_WhenTheImportHasFinished() {
        autenticadoComoAdministrador();
        UUID id = UUID.randomUUID();
        when(registro.solicitarCancelamento(id, "admin@fiesc.org.br"))
                .thenThrow(new ImportacaoNaoEmAndamentoException("finalizada"));

        assertThrows(ImportacaoNaoEmAndamentoException.class, () -> service.cancelar(id.toString()));
    }
}
