package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.ResumoImportacaoPostos;
import com.github.api_abastecefacil.exception.ImportacaoEmAndamentoException;
import com.github.api_abastecefacil.exception.ImportacaoNaoEncontradaException;
import com.github.api_abastecefacil.model.StatusImportacao;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.IMPORTACAO_EM_ANDAMENTO_MESSAGE;
import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.IMPORTACAO_NAO_ENCONTRADA_MESSAGE;

/**
 * Situação das importações de postos, em memória.
 *
 * <p><b>Uma importação por vez, de forma atômica.</b> {@link #iniciar} faz
 * {@code compareAndSet(null, id)} num {@link AtomicReference}: duas requisições simultâneas
 * nunca passam juntas, sem trava e sem janela entre conferir e registrar. A vaga só é
 * devolvida em {@link #finalizar}, <b>depois</b> de o desfecho estar gravado — quem consultar
 * logo em seguida já vê o status final.
 *
 * <p><b>Os valores são imutáveis.</b> A thread da importação escreve e as threads de
 * requisição leem; cada avanço substitui o record por {@code computeIfPresent}, então
 * nenhuma leitura vê um objeto pela metade.
 *
 * <p><b>Retenção de 24 horas, com limpeza oportunista.</b> Os finalizados há mais de 24 horas
 * são removidos ao iniciar e ao consultar — não há {@code @Scheduled}. São poucos registros
 * por dia, então a varredura é barata e não justifica um agendamento. Um registro
 * {@code EM_ANDAMENTO} nunca é removido.
 *
 * <p><b>Limitação conhecida, como no {@code RateLimitService}: o estado é por instância e se
 * perde no reinício.</b> Com mais de um nó, cada um teria a sua vaga e duas importações
 * poderiam rodar juntas. Num reinício, a importação em curso morre com a aplicação: o que
 * já foi gravado fica, e a próxima importação recalcula o plano sobre o banco.
 */
@Service
public class RegistroImportacoesPostos {

    static final Duration RETENCAO = Duration.ofHours(24);

    private final Clock clock;
    private final Map<UUID, ImportacaoPostosStatus> registros = new ConcurrentHashMap<>();
    private final AtomicReference<UUID> emAndamento = new AtomicReference<>();

    public RegistroImportacoesPostos(Clock clock) {
        this.clock = clock;
    }

    /**
     * @throws ImportacaoEmAndamentoException se já houver uma importação em andamento
     */
    public ImportacaoPostosStatus iniciar(int total) {
        descartarExpirados();
        UUID id = UUID.randomUUID();

        if (!emAndamento.compareAndSet(null, id)) {
            throw new ImportacaoEmAndamentoException(String.format(IMPORTACAO_EM_ANDAMENTO_MESSAGE, emAndamento.get()));
        }

        ImportacaoPostosStatus status = new ImportacaoPostosStatus(id, StatusImportacao.EM_ANDAMENTO, total, 0,
                LocalDateTime.now(clock), null, null, null);
        registros.put(id, status);
        return status;
    }

    public void avancar(UUID id) {
        registros.computeIfPresent(id, (chave, status) -> status.comMaisUmProcessado());
    }

    /** Grava o desfecho e só então libera a vaga para a próxima importação. */
    public void finalizar(UUID id, StatusImportacao status, String mensagem, ResumoImportacaoPostos resumo) {
        LocalDateTime agora = LocalDateTime.now(clock);
        registros.computeIfPresent(id, (chave, atual) -> atual.finalizada(status, agora, mensagem, resumo));
        emAndamento.compareAndSet(id, null);
    }

    /**
     * @throws ImportacaoNaoEncontradaException se o id não existe ou já foi descartado
     */
    public ImportacaoPostosStatus consultar(UUID id) {
        descartarExpirados();
        ImportacaoPostosStatus status = registros.get(id);
        if (status == null) {
            throw new ImportacaoNaoEncontradaException(IMPORTACAO_NAO_ENCONTRADA_MESSAGE);
        }
        return status;
    }

    /**
     * A importação em andamento, se houver. Só leitura: não toma nem libera a vaga.
     *
     * <p>O filtro por {@code EM_ANDAMENTO} cobre o instante entre {@link #finalizar} gravar o
     * desfecho e liberar a vaga: nesse intervalo o id ainda está em {@code emAndamento}, mas
     * a importação já terminou e não deve ser devolvida como atual.
     */
    public Optional<ImportacaoPostosStatus> atual() {
        UUID id = emAndamento.get();
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registros.get(id))
                .filter(status -> status.status() == StatusImportacao.EM_ANDAMENTO);
    }

    private void descartarExpirados() {
        LocalDateTime limite = LocalDateTime.now(clock).minus(RETENCAO);
        registros.values().removeIf(status -> status.concluidaEm() != null && status.concluidaEm().isBefore(limite));
    }
}
