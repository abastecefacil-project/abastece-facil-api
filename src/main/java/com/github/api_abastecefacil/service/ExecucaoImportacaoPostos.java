package com.github.api_abastecefacil.service;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.SEGUNDOS_ESPERA_DESLIGAMENTO;
import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.THREAD_IMPORTACAO_PREFIXO;

/**
 * A thread dedicada da importação de postos.
 *
 * <p><b>Por que um componente dono do executor, e não um bean {@code Executor}.</b> No Spring
 * Boot 3.5 o {@code applicationTaskExecutor} só é criado se não houver <b>nenhum</b> bean do
 * tipo {@code java.util.concurrent.Executor}. Publicar o executor da importação como bean
 * desligaria o padrão em silêncio, e o {@code @Async} da recuperação de senha passaria a usar
 * esta thread única — os e-mails de recuperação esperariam na fila atrás de uma importação de
 * vinte minutos. Este componente <b>não é</b> um {@code Executor}: o
 * {@link ThreadPoolTaskExecutor} é um campo privado, invisível para a autoconfiguração. Ver
 * §9 do CLAUDE.md.
 *
 * <p>Uma thread só, com fila 1: o {@code RegistroImportacoesPostos} já garante uma importação
 * por vez, e a vaga na fila cobre o instante em que a anterior liberou o registro mas a
 * thread ainda não voltou ao pool.
 *
 * <p><b>Desligamento.</b> Como o executor não é bean, o Spring não gerencia o ciclo de vida
 * dele, e o {@link #encerrar} no {@code @PreDestroy} é obrigatório — sem ele a thread não
 * seria encerrada. {@code waitForTasksToCompleteOnShutdown=false} interrompe a thread em
 * vez de esperar a importação acabar; a interrupção acorda a espera do throttle do
 * {@code OpenStreetMapService}, e a importação termina como {@code FALHOU}. O
 * {@code awaitTermination} de 10 s dá tempo ao item que estiver gravando, sem nunca segurar
 * o shutdown indefinidamente.
 */
@Component
public class ExecucaoImportacaoPostos {

    private final ThreadPoolTaskExecutor executor;

    public ExecucaoImportacaoPostos() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix(THREAD_IMPORTACAO_PREFIXO);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(SEGUNDOS_ESPERA_DESLIGAMENTO);
        executor.initialize();
    }

    /**
     * @throws org.springframework.core.task.TaskRejectedException se o executor já foi
     *                                                            encerrado ou está ocupado
     */
    public void submeter(Runnable tarefa) {
        executor.execute(tarefa);
    }

    @PreDestroy
    public void encerrar() {
        executor.shutdown();
    }
}
