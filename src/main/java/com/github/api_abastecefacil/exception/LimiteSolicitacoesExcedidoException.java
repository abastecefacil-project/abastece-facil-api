package com.github.api_abastecefacil.exception;

/**
 * Solicitação recusada por exceder o limite de requisições da janela corrente.
 *
 * <p>Traduzida para <b>429 Too Many Requests</b> pelo {@code GlobalExceptionHandler} —
 * o primeiro 429 do projeto. O handler é registrado junto com a exceção porque não há
 * fallback {@code Exception.class}: exceção sem handler escapa para o
 * {@code BasicErrorController} e vira 500 cru, sem {@code ErrorResponse}.
 */
public class LimiteSolicitacoesExcedidoException extends RuntimeException {

    public LimiteSolicitacoesExcedidoException(String message) {
        super(message);
    }
}
