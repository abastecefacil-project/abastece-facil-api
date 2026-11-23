package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.PreviaImportacaoResponse;
import com.github.api_abastecefacil.exception.DesativacaoNaoConfirmadaException;
import com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException;
import com.github.api_abastecefacil.exception.PlanilhaInvalidaException;
import com.github.api_abastecefacil.exception.PlanoImportacaoDivergenteException;
import com.github.api_abastecefacil.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * gravação. A ciência das desativações (ver {@link #iniciar}) é conferida contra esse plano
 * recalculado, pelo mesmo motivo.
 *
 * <p><b>Não é {@code @Transactional}, de propósito.</b> Ler uma planilha de alguns MB leva
 * segundos, e uma transação aberta aqui seguraria uma conexão do pool durante a leitura. O
 * planejador abre a sua própria, só de leitura e curta.
 */
@Service
public class ImportacaoPostosService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoPostosService.class);

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
     * Recalcula o plano e só então o entrega ao executor, que reserva a vaga da importação.
     *
     * <p><b>Desativar exige ciência explícita.</b> {@code desativacoesConfirmadas} é a
     * quantidade de desativações que o administrador viu e confirmou na prévia. Ela é
     * conferida contra o plano <b>recalculado</b>, antes de reservar a importação, então nos
     * dois casos recusados nada é gravado e a vaga continua livre:
     *
     * <ul>
     *   <li>plano com desativações e parâmetro ausente: {@link DesativacaoNaoConfirmadaException};</li>
     *   <li>parâmetro diferente da quantidade do plano recalculado — inclusive um valor
     *       informado para um plano que já não desativa nada: {@link PlanoImportacaoDivergenteException}.
     *       A ciência foi dada sobre outro cadastro e não vale para este.</li>
     * </ul>
     *
     * <p>Plano sem desativações dispensa o parâmetro. A regra de quem é desativado não muda:
     * continua sendo do {@link PlanejadorImportacaoPostos}; aqui muda só a condição para
     * começar.
     *
     * @param desativacoesConfirmadas quantidade confirmada na prévia, ou {@code null}
     * @return o id da importação, que segue em segundo plano
     */
    public UUID iniciar(MultipartFile arquivo, Integer desativacoesConfirmadas) {
        autorizacaoOperacional.autorizarAdministracao();
        PlanoImportacao plano = planejar(arquivo);
        exigirCienciaDasDesativacoes(plano, desativacoesConfirmadas);
        return executor.iniciar(plano);
    }

    private static void exigirCienciaDasDesativacoes(PlanoImportacao plano, Integer desativacoesConfirmadas) {
        int desativacoes = plano.desativar().size();
        if (desativacoesConfirmadas == null) {
            if (desativacoes > 0) {
                throw new DesativacaoNaoConfirmadaException(
                        String.format(DESATIVACAO_NAO_CONFIRMADA_MESSAGE, formatarQuantidadePostos(desativacoes)));
            }
            return;
        }
        if (desativacoesConfirmadas != desativacoes) {
            throw new PlanoImportacaoDivergenteException(
                    String.format(PLANO_IMPORTACAO_DIVERGENTE_MESSAGE, desativacoesConfirmadas, desativacoes));
        }
    }

    /**
     * "1 posto" ou "N postos". Mesmo desenho do {@code ConteudoEmail.formatarValidade}: o caso
     * de 1 existe só para a mensagem concordar com o número.
     */
    private static String formatarQuantidadePostos(int quantidade) {
        return quantidade == 1 ? QUANTIDADE_UM_POSTO : String.format(QUANTIDADE_POSTOS_FORMATO, quantidade);
    }

    /**
     * @see #paraUuid
     */
    public ImportacaoPostosStatus consultar(String id) {
        autorizacaoOperacional.autorizarAdministracao();
        return executor.consultar(paraUuid(id));
    }

    public Optional<ImportacaoPostosStatus> atual() {
        autorizacaoOperacional.autorizarAdministracao();
        return registro.atual();
    }

    /**
     * Registra o pedido de cancelamento e devolve o status atual, ainda {@code EM_ANDAMENTO}:
     * a parada é cooperativa e acontece no próximo ponto de verificação do executor.
     *
     * <p>O e-mail de quem cancelou é lido <b>aqui</b>, na thread da requisição, e guardado no
     * registro — a thread da importação não tem {@code SecurityContext}.
     *
     * @throws com.github.api_abastecefacil.exception.ImportacaoNaoEmAndamentoException se a
     *                                                                                  importação
     *                                                                                  já terminou
     */
    public ImportacaoPostosStatus cancelar(String id) {
        User autor = autorizacaoOperacional.autorizarAdministracao();

        UUID uuid = paraUuid(id);
        ImportacaoPostosStatus status = registro.solicitarCancelamento(uuid, autor.getEmail());
        log.info("Cancelamento da importação de postos {} solicitado por {} após {} de {} itens",
                uuid, autor.getEmail(), status.processados(), status.total());
        return status;
    }

    /**
     * Recebe o id como texto: um valor que não é UUID responde 404, como um id que não existe,
     * em vez de virar erro de conversão sem handler.
     */
    private static UUID paraUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ImportacaoNaoEncontradaException(IMPORTACAO_NAO_ENCONTRADA_MESSAGE);
        }
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
