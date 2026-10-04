package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.ItemPlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.dto.gasStation.OcorrenciaPlanilha;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.ResumoImportacaoPostos;
import com.github.api_abastecefacil.model.StatusImportacao;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.*;

/**
 * Executa um {@link PlanoImportacao} em segundo plano, com progresso consultável.
 *
 * <p>A primeira carga tem cerca de 1.200 inserções e o Nominatim aceita uma requisição por
 * segundo: são uns vinte minutos, que não cabem numa requisição HTTP nem numa transação.
 * {@link #iniciar} registra, submete à thread da {@link ExecucaoImportacaoPostos} e devolve o
 * id na hora; {@link #consultar} mostra o andamento.
 *
 * <p><b>Sem autorização aqui, de propósito.</b> Ela é responsabilidade do chamador, na thread
 * da requisição: a thread da importação não tem {@code SecurityContext}, e qualquer coisa que
 * consulte o {@code UsuarioAutenticadoProvider} falharia nela. Pelo mesmo motivo os postos são
 * gravados pelo {@link GravadorImportacaoPostos}, e não pelo {@code GasStationService}, cujo
 * {@code create}/{@code update} autorizam e sempre regeocodificam.
 *
 * <p><b>Ordem de execução:</b>
 * <ol>
 *   <li>ATUALIZAR e depois REATIVAR, só os que não requerem geocodificação. Não chamam o
 *       Nominatim, então terminam em segundos;</li>
 *   <li>INSERIR, depois ATUALIZAR e REATIVAR que requerem geocodificação. A chamada ao
 *       Nominatim acontece fora de transação, e o throttle é do {@code OpenStreetMapService}:
 *       aqui não há espera própria;</li>
 *   <li>DESATIVAR, por último, e só se a importação não foi interrompida. Desativar com a
 *       sincronização pela metade tiraria postos do mapa sem que os novos tivessem entrado.</li>
 * </ol>
 *
 * <p><b>Falha de geocodificação de endereço alterado</b> (ATUALIZAR e REATIVAR), seja
 * resultado vazio ou erro de comunicação: grava só os campos que não são de endereço e mantém
 * endereço e coordenadas. Endereço novo com coordenada antiga deixaria o marcador no lugar
 * errado em silêncio; mantendo o endereço antigo, a diferença reaparece e a próxima importação
 * tenta de novo. Vazio vira aviso; erro de comunicação vira erro e conta para o limite abaixo.
 *
 * <p><b>Falhas de comunicação consecutivas.</b> Cada {@code FeignException} soma 1; qualquer
 * resposta válida do Nominatim, com ou sem resultado, zera. Ao chegar a
 * {@code importacao-postos.max-falhas-consecutivas} a importação para como {@code FALHOU}: com
 * o serviço fora ou bloqueando, continuar só acumularia erro item a item por vinte minutos.
 * Erro ao <b>gravar</b> um item não conta: é do item — um CNPJ cadastrado à mão durante a
 * importação, por exemplo — e os demais seguem.
 */
@Service
public class ExecutorImportacaoPostos {

    private static final Logger log = LoggerFactory.getLogger(ExecutorImportacaoPostos.class);

    private final RegistroImportacoesPostos registro;
    private final ExecucaoImportacaoPostos execucao;
    private final OpenStreetMapService openStreetMapService;
    private final GravadorImportacaoPostos gravador;
    private final int maxFalhasConsecutivas;

    public ExecutorImportacaoPostos(
            RegistroImportacoesPostos registro,
            ExecucaoImportacaoPostos execucao,
            OpenStreetMapService openStreetMapService,
            GravadorImportacaoPostos gravador,
            @Value("${importacao-postos.max-falhas-consecutivas:5}") int maxFalhasConsecutivas
    ) {
        this.registro = registro;
        this.execucao = execucao;
        this.openStreetMapService = openStreetMapService;
        this.gravador = gravador;
        this.maxFalhasConsecutivas = maxFalhasConsecutivas;
    }

    /**
     * Registra a importação, submete à thread dedicada e devolve o id imediatamente.
     *
     * @throws com.github.api_abastecefacil.exception.ImportacaoEmAndamentoException se já
     *                                                                              houver uma
     */
    public UUID iniciar(PlanoImportacao plano) {
        int total = plano.inserir().size() + plano.atualizar().size()
                + plano.reativar().size() + plano.desativar().size();
        UUID id = registro.iniciar(total).id();
        log.info("Importação de postos {} iniciada: {} itens", id, total);

        try {
            execucao.submeter(() -> executar(id, plano, total));
        } catch (RuntimeException e) {
            // Só acontece no desligamento. Sem isto a vaga ficaria presa até o reinício.
            registro.finalizar(id, StatusImportacao.FALHOU, String.format(IMPORTACAO_INTERROMPIDA_ERRO_MESSAGE, 0, total),
                    null);
            throw e;
        }
        return id;
    }

    /**
     * @throws com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException se o id
     *                                                                                não existe
     */
    public ImportacaoPostosStatus consultar(UUID id) {
        return registro.consultar(id);
    }

    private void executar(UUID id, PlanoImportacao plano, int total) {
        Execucao execucaoAtual = new Execucao(id);

        try {
            execucaoAtual.processar(plano);
        } catch (RuntimeException e) {
            log.error("Importação de postos {} interrompida por erro inesperado após {} de {} itens",
                    id, execucaoAtual.processados, total, e);
            registro.finalizar(id, StatusImportacao.FALHOU,
                    String.format(IMPORTACAO_INTERROMPIDA_ERRO_MESSAGE, execucaoAtual.processados, total),
                    execucaoAtual.resumo(plano));
            return;
        } catch (Error e) {
            registro.finalizar(id, StatusImportacao.FALHOU,
                    String.format(IMPORTACAO_INTERROMPIDA_ERRO_MESSAGE, execucaoAtual.processados, total), null);
            throw e;
        }

        ResumoImportacaoPostos resumo = execucaoAtual.resumo(plano);

        if (execucaoAtual.interrompida) {
            String mensagem = String.format(IMPORTACAO_INTERROMPIDA_FALHAS_MESSAGE,
                    execucaoAtual.falhasConsecutivas, execucaoAtual.processados, total);
            log.warn("Importação de postos {}: {}", id, mensagem);
            registro.finalizar(id, StatusImportacao.FALHOU, mensagem, resumo);
            return;
        }

        log.info("Importação de postos {} concluída: {} inseridos, {} atualizados, {} reativados, {} desativados, "
                        + "{} sem alteração, {} erros, {} avisos",
                id, resumo.inseridos(), resumo.atualizados(), resumo.reativados(), resumo.desativados(),
                resumo.semAlteracao(), resumo.erros().size(), resumo.avisos().size());
        registro.finalizar(id, StatusImportacao.CONCLUIDA, IMPORTACAO_CONCLUIDA_MESSAGE, resumo);
    }

    /** Estado de uma execução. Vive só na thread da importação, então não precisa de trava. */
    private final class Execucao {

        private final UUID id;
        private final List<OcorrenciaPlanilha> erros = new ArrayList<>();
        private final List<OcorrenciaPlanilha> avisos = new ArrayList<>();
        private int inseridos;
        private int atualizados;
        private int reativados;
        private int desativados;
        private int processados;
        private int falhasConsecutivas;
        private boolean interrompida;

        private Execucao(UUID id) {
            this.id = id;
        }

        private void processar(PlanoImportacao plano) {
            for (ItemPlanoImportacao item : plano.atualizar()) {
                if (!item.requerGeocodificacao()) {
                    processarItem(() -> atualizarSemGeocodificar(item, false));
                }
            }
            for (ItemPlanoImportacao item : plano.reativar()) {
                if (!item.requerGeocodificacao()) {
                    processarItem(() -> atualizarSemGeocodificar(item, true));
                }
            }

            for (ItemPlanoImportacao item : plano.inserir()) {
                if (interrompida) {
                    return;
                }
                processarItem(() -> inserir(item));
            }
            for (ItemPlanoImportacao item : plano.atualizar()) {
                if (interrompida) {
                    return;
                }
                if (item.requerGeocodificacao()) {
                    processarItem(() -> atualizarGeocodificando(item, false));
                }
            }
            for (ItemPlanoImportacao item : plano.reativar()) {
                if (interrompida) {
                    return;
                }
                if (item.requerGeocodificacao()) {
                    processarItem(() -> atualizarGeocodificando(item, true));
                }
            }

            if (interrompida) {
                return;
            }
            for (ItemPlanoImportacao item : plano.desativar()) {
                processarItem(() -> gravar(item, () -> gravador.desativar(item.id()), () -> desativados++));
            }
        }

        /** Conta o item como processado aconteça o que acontecer com ele. */
        private void processarItem(Runnable processamento) {
            try {
                processamento.run();
            } finally {
                processados++;
                registro.avancar(id);
            }
        }

        private void atualizarSemGeocodificar(ItemPlanoImportacao item, boolean reativar) {
            gravar(item, () -> gravador.atualizar(item.id(), item.dados(), reativar), contador(reativar));
        }

        private void inserir(ItemPlanoImportacao item) {
            LinhaPlanilhaPosto linha = item.dados();
            Optional<Coordinates> coordenadas;
            try {
                coordenadas = geocodificar(linha);
            } catch (FeignException e) {
                falhaDeComunicacao(item, FALHA_GEOCODIFICACAO_INSERIR_MESSAGE, e);
                return;
            }

            if (coordenadas.isEmpty()) {
                erros.add(ocorrencia(item, COORDENADAS_NAO_ENCONTRADAS_INSERIR_MESSAGE));
                return;
            }
            gravar(item, () -> gravador.inserir(linha, coordenadas.get()), () -> inseridos++);
        }

        private void atualizarGeocodificando(ItemPlanoImportacao item, boolean reativar) {
            LinhaPlanilhaPosto linha = item.dados();
            Optional<Coordinates> coordenadas;
            try {
                coordenadas = geocodificar(linha);
            } catch (FeignException e) {
                falhaDeComunicacao(item, FALHA_GEOCODIFICACAO_ATUALIZAR_MESSAGE, e);
                gravar(item, () -> gravador.atualizarSemEndereco(item.id(), linha, reativar), contador(reativar));
                return;
            }

            if (coordenadas.isEmpty()) {
                avisos.add(ocorrencia(item, COORDENADAS_NAO_ENCONTRADAS_ATUALIZAR_MESSAGE));
                gravar(item, () -> gravador.atualizarSemEndereco(item.id(), linha, reativar), contador(reativar));
                return;
            }
            gravar(item, () -> gravador.atualizarComCoordenadas(item.id(), linha, coordenadas.get(), reativar),
                    contador(reativar));
        }

        /**
         * Uma resposta, com ou sem resultado, prova que o serviço está respondendo e zera o
         * contador. A {@code FeignException} sai daqui sem ser tratada; a
         * {@code IllegalStateException} da espera interrompida também, e encerra a importação.
         */
        private Optional<Coordinates> geocodificar(LinhaPlanilhaPosto linha) {
            Optional<Coordinates> coordenadas = openStreetMapService.geocodificarComFallback(
                    linha.address(), linha.district(), linha.city(), linha.state(), linha.cep(), linha.state());
            falhasConsecutivas = 0;
            return coordenadas;
        }

        /** Só o status HTTP vai para o log: o corpo da resposta do provedor fica de fora. */
        private void falhaDeComunicacao(ItemPlanoImportacao item, String mensagem, FeignException e) {
            erros.add(ocorrencia(item, mensagem));
            falhasConsecutivas++;
            log.warn("Importação de postos {}: falha de comunicação com o Nominatim (status {}), {} consecutiva(s)",
                    id, e.status(), falhasConsecutivas);
            if (falhasConsecutivas >= maxFalhasConsecutivas) {
                interrompida = true;
            }
        }

        /** Erro ao gravar é do item: registra e segue para o próximo. */
        private void gravar(ItemPlanoImportacao item, Runnable gravacao, Runnable contagem) {
            try {
                gravacao.run();
                contagem.run();
            } catch (DataIntegrityViolationException e) {
                log.warn("Importação de postos {}: conflito ao gravar o posto de CNPJ {}", id, item.cnpj(), e);
                erros.add(ocorrencia(item, CONFLITO_GRAVACAO_MESSAGE));
            } catch (RuntimeException e) {
                log.error("Importação de postos {}: erro ao gravar o posto de CNPJ {}", id, item.cnpj(), e);
                erros.add(ocorrencia(item, ERRO_GRAVACAO_MESSAGE));
            }
        }

        private Runnable contador(boolean reativar) {
            return reativar ? () -> reativados++ : () -> atualizados++;
        }

        private ResumoImportacaoPostos resumo(PlanoImportacao plano) {
            List<OcorrenciaPlanilha> todosErros = new ArrayList<>(plano.erros());
            todosErros.addAll(erros);
            List<OcorrenciaPlanilha> todosAvisos = new ArrayList<>(plano.avisos());
            todosAvisos.addAll(avisos);
            return new ResumoImportacaoPostos(inseridos, atualizados, reativados, desativados, plano.semAlteracao(),
                    List.copyOf(todosErros), List.copyOf(todosAvisos));
        }
    }

    /** Item de desativar não tem linha da planilha: sai com 0. Ver {@link OcorrenciaPlanilha}. */
    private static OcorrenciaPlanilha ocorrencia(ItemPlanoImportacao item, String mensagem) {
        int linha = item.dados() == null ? LINHA_FORA_DA_PLANILHA : item.dados().numeroLinha();
        return new OcorrenciaPlanilha(linha, item.cnpj(), mensagem);
    }
}
