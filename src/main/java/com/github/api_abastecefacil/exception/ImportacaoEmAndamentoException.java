package com.github.api_abastecefacil.exception;

/**
 * Já existe uma importação de postos em andamento. Só roda uma por vez: duas sincronizações
 * simultâneas calculadas sobre o mesmo banco desativariam e atualizariam postos uma por
 * cima da outra.
 */
public class ImportacaoEmAndamentoException extends RuntimeException {

    public ImportacaoEmAndamentoException(String message) {
        super(message);
    }
}
