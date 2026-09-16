package com.github.api_abastecefacil.exception;

/**
 * Falha ao entregar um e-mail ao provedor de envio.
 *
 * <p><b>É a primeira exceção do projeto a aceitar {@code cause}</b>, e o desvio do molde
 * das outras é deliberado. A falha de I/O do {@code RestClient} — DNS, timeout, conexão
 * recusada — precisa aparecer no stack trace do log para ser diagnosticável, mas não
 * pode entrar na mensagem devolvida ao cliente. Encadear a causa separa as duas coisas:
 * {@link #getMessage()} continua sendo o texto genérico que o
 * {@code GlobalExceptionHandler} publica, e o detalhe fica no log.
 *
 * <p>O construtor só de mensagem existe para o caminho de resposta HTTP de erro, onde
 * não há exceção original a encadear — a rejeição do Resend é um status, não um
 * {@code Throwable}.
 */
public class EnvioEmailException extends RuntimeException {

    public EnvioEmailException(String message) {
        super(message);
    }

    public EnvioEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
