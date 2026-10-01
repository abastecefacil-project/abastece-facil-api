package com.github.api_abastecefacil.exception;

/**
 * O id não corresponde a nenhuma importação de postos conhecida: nunca existiu, foi
 * finalizada há mais de 24 horas e descartada, ou a aplicação reiniciou — o registro é em
 * memória.
 */
public class ImportacaoNaoEncontradaException extends RuntimeException {

    public ImportacaoNaoEncontradaException(String message) {
        super(message);
    }
}
