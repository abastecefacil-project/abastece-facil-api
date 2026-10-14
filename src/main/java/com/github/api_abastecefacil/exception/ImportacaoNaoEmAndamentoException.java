package com.github.api_abastecefacil.exception;

/**
 * Pedido de cancelamento para uma importação de postos que já terminou — concluída, falha ou
 * cancelada. Não há o que interromper: o que foi gravado está gravado.
 */
public class ImportacaoNaoEmAndamentoException extends RuntimeException {

    public ImportacaoNaoEmAndamentoException(String message) {
        super(message);
    }
}
