package com.github.api_abastecefacil.exception;

/**
 * A planilha de postos não pode ser lida: não é .xlsx, não tem o cabeçalho esperado ou
 * falta coluna obrigatória. Problema de uma linha isolada não é isto — vira erro da linha.
 *
 * <p>Aceita {@code cause} pelo mesmo motivo do {@link EnvioEmailException}: a falha do
 * POI precisa aparecer no stack trace do log, mas a mensagem dela descreve a estrutura
 * interna do zip e não serve a quem enviou o arquivo. {@link #getMessage()} é sempre o
 * texto em pt-BR que o {@code GlobalExceptionHandler} publica.
 */
public class PlanilhaInvalidaException extends RuntimeException {

    public PlanilhaInvalidaException(String message) {
        super(message);
    }

    public PlanilhaInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
