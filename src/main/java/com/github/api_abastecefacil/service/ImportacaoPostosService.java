package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.PreviaImportacaoResponse;
import com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException;
import com.github.api_abastecefacil.exception.PlanilhaInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.*;

/**
 * Fachada da importação da planilha de postos: o que os endpoints de
 * {@code /api/gas-stations/import} chamam.
 *
 * <p><b>Só ADMINISTRADOR, e a guarda é a primeira instrução de cada método</b>, na thread da
 * requisição — antes de ler o arquivo, de consultar o banco ou de tocar no registro. A
 * autorização tem de acontecer aqui porque a thread da importação não tem
 * {@code SecurityContext}.
 *
 * <p><b>A prévia e a importação recalculam o plano a partir do arquivo.</b> {@link #iniciar}
 * não reaproveita prévia nenhuma: entre a prévia e o clique o banco pode ter mudado, e um
 * plano guardado seria aplicado sobre um cadastro que ele não descreve mais. Por isso os
 * erros de leitura (400) e de planejamento (422) saem de forma síncrona, antes de qualquer
 * gravação.
 *
 * <p><b>Não é {@code @Transactional}, de propósito.</b> Ler uma planilha de alguns MB leva
 * segundos, e uma transação aberta aqui seguraria uma conexão do pool durante a leitura. O
 * planejador abre a sua própria, só de leitura e curta.
 */
@Service
public class ImportacaoPostosService {

    private final AutorizacaoOperacional autorizacaoOperacional;
    private final LeitorPlanilhaPostos leitor;
    private final PlanejadorImportacaoPostos planejador;
    private final ExecutorImportacaoPostos executor;
    private final RegistroImportacoesPostos registro;

    public ImportacaoPostosService(
            AutorizacaoOperacional autorizacaoOperacional,
            LeitorPlanilhaPostos leitor,
            PlanejadorImportacaoPostos planejador,
            ExecutorImportacaoPostos executor,
            RegistroImportacoesPostos registro
    ) {
        this.autorizacaoOperacional = autorizacaoOperacional;
        this.leitor = leitor;
        this.planejador = planejador;
        this.executor = executor;
        this.registro = registro;
    }

    public PreviaImportacaoResponse previa(MultipartFile arquivo) {
        autorizacaoOperacional.autorizarAdministracao();
        return PreviaImportacaoResponse.de(planejar(arquivo));
    }

    /**
     * @return o id da importação, que segue em segundo plano
     */
    public UUID iniciar(MultipartFile arquivo) {
        autorizacaoOperacional.autorizarAdministracao();
        return executor.iniciar(planejar(arquivo));
    }

    /**
     * Recebe o id como texto: um valor que não é UUID responde 404, como um id que não existe,
     * em vez de virar erro de conversão sem handler.
     */
    public ImportacaoPostosStatus consultar(String id) {
        autorizacaoOperacional.autorizarAdministracao();

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ImportacaoNaoEncontradaException(IMPORTACAO_NAO_ENCONTRADA_MESSAGE);
        }
        return executor.consultar(uuid);
    }

    public Optional<ImportacaoPostosStatus> atual() {
        autorizacaoOperacional.autorizarAdministracao();
        return registro.atual();
    }

    private PlanoImportacao planejar(MultipartFile arquivo) {
        validar(arquivo);
        try (InputStream conteudo = arquivo.getInputStream()) {
            return planejador.planejar(leitor.ler(conteudo));
        } catch (IOException e) {
            // Falha ao ler o arquivo temporário do upload. Sem tradução, a IOException não
            // teria handler e cairia no 403 vazio do /error (ver §5 do CLAUDE.md).
            throw new PlanilhaInvalidaException(ARQUIVO_ILEGIVEL_MESSAGE, e);
        }
    }

    private static void validar(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new PlanilhaInvalidaException(ARQUIVO_VAZIO_MESSAGE);
        }
        String nome = arquivo.getOriginalFilename();
        if (nome == null || !nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_XLSX)) {
            throw new PlanilhaInvalidaException(PLANILHA_INVALIDA_MESSAGE);
        }
    }
}
