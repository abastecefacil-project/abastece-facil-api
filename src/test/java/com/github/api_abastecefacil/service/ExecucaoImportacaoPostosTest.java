package com.github.api_abastecefacil.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Thread dedicada da importação. Usa uma thread real, sem sleep: o teste espera o resultado
 * da tarefa com prazo, e não um tempo fixo.
 */
class ExecucaoImportacaoPostosTest {

    private ExecucaoImportacaoPostos execucao;

    @BeforeEach
    void setUp() {
        execucao = new ExecucaoImportacaoPostos();
    }

    @AfterEach
    void tearDown() {
        execucao.encerrar();
    }

    @Test
    void submeter_ShouldRunTheTaskOnAThreadNamedAfterTheImport() throws Exception {
        CompletableFuture<String> nomeDaThread = new CompletableFuture<>();

        execucao.submeter(() -> nomeDaThread.complete(Thread.currentThread().getName()));

        assertThat(nomeDaThread.get(5, TimeUnit.SECONDS)).startsWith("importacao-postos-");
    }

    @Test
    void submeter_ShouldRejectTasks_AfterShutdown() {
        execucao.encerrar();

        assertThatThrownBy(() -> execucao.submeter(() -> { }))
                .isInstanceOf(TaskRejectedException.class);
    }

    /**
     * A premissa que evita a armadilha do §9: se este componente fosse um Executor, a
     * autoconfiguração do Spring Boot deixaria de criar o applicationTaskExecutor.
     */
    @Test
    void execucao_ShouldNotBeAnExecutor() {
        assertThat(execucao).isNotInstanceOf(Executor.class);
    }
}
