package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.ResumoImportacaoPostos;
import com.github.api_abastecefacil.exception.ImportacaoEmAndamentoException;
import com.github.api_abastecefacil.exception.ImportacaoNaoEmAndamentoException;
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
import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.IMPORTACAO_NAO_EM_ANDAMENTO_MESSAGE;
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
 * <p><b>O pedido de cancelamento mora na mesma entrada que o status.</b> Assim
 * {@link #solicitarCancelamento} confere {@code EM_ANDAMENTO} e registra o pedido num único
 * {@code compute}, atômico em relação ao {@code computeIfPresent} de {@link #finalizar}: um
 * pedido nunca é aceito para uma importação que já terminou. O e-mail de quem pediu fica fora
 * do {@link ImportacaoPostosStatus}, que é o corpo das respostas HTTP.
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
    private final Map<UUID, Entrada> registros = new ConcurrentHashMap<>();
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
        registros.put(id, new Entrada(status, null));
        return status;
    }

    public void avancar(UUID id) {
        registros.computeIfPresent(id, (chave, entrada) -> entrada.comStatus(entrada.status().comMaisUmProcessado()));
    }

    /** Grava o desfecho e só então libera a vaga para a próxima importação. */
    public void finalizar(UUID id, StatusImportacao status, String mensagem, ResumoImportacaoPostos resumo) {
        LocalDateTime agora = LocalDateTime.now(clock);
        registros.computeIfPresent(id,
                (chave, entrada) -> entrada.comStatus(entrada.status().finalizada(status, agora, mensagem, resumo)));
        emAndamento.compareAndSet(id, null);
    }

    /**
     * @throws ImportacaoNaoEncontradaException se o id não existe ou já foi descartado
     */
    public ImportacaoPostosStatus consultar(UUID id) {
        descartarExpirados();
        return buscar(id).status();
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
                .map(Entrada::status)
                .filter(status -> status.status() == StatusImportacao.EM_ANDAMENTO);
    }

    /**
     * Registra o pedido de cancelamento. Só o registra: quem para é o executor, no próximo
     * ponto de verificação. Um segundo pedido para a mesma importação é aceito e mantém o
     * autor do primeiro.
     *
     * @param email quem pediu, lido na thread da requisição — a da importação não tem
     *              {@code SecurityContext}
     * @return o status no momento do pedido, ainda {@code EM_ANDAMENTO}
     * @throws ImportacaoNaoEncontradaException  se o id não existe ou já foi descartado
     * @throws ImportacaoNaoEmAndamentoException se a importação já terminou
     */
    public ImportacaoPostosStatus solicitarCancelamento(UUID id, String email) {
        descartarExpirados();
        Entrada entrada = registros.compute(id, (chave, atual) -> {
            if (atual == null) {
                throw new ImportacaoNaoEncontradaException(IMPORTACAO_NAO_ENCONTRADA_MESSAGE);
            }
            if (atual.status().status() != StatusImportacao.EM_ANDAMENTO) {
                throw new ImportacaoNaoEmAndamentoException(
                        String.format(IMPORTACAO_NAO_EM_ANDAMENTO_MESSAGE, atual.status().status()));
            }
            return atual.canceladoPor() != null ? atual : new Entrada(atual.status(), email);
        });
        return entrada.status();
    }

    /** O e-mail de quem pediu o cancelamento, se alguém pediu. Consultado pelo executor. */
    public Optional<String> cancelamentoSolicitadoPor(UUID id) {
        return Optional.ofNullable(registros.get(id)).map(Entrada::canceladoPor);
    }

    private Entrada buscar(UUID id) {
        Entrada entrada = registros.get(id);
        if (entrada == null) {
            throw new ImportacaoNaoEncontradaException(IMPORTACAO_NAO_ENCONTRADA_MESSAGE);
        }
        return entrada;
    }

    private void descartarExpirados() {
        LocalDateTime limite = LocalDateTime.now(clock).minus(RETENCAO);
        registros.values().removeIf(entrada -> entrada.status().concluidaEm() != null
                && entrada.status().concluidaEm().isBefore(limite));
    }

    /** @param canceladoPor {@code null} enquanto ninguém pediu o cancelamento */
    private record Entrada(ImportacaoPostosStatus status, String canceladoPor) {

        private Entrada comStatus(ImportacaoPostosStatus novoStatus) {
            return new Entrada(novoStatus, canceladoPor);
        }
    }
}
