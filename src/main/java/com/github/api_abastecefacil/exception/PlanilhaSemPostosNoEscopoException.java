package com.github.api_abastecefacil.exception;

/**
 * A planilha foi lida, mas nenhuma linha válida caiu no escopo da importação.
 *
 * <p>Existe para impedir desativação em massa: sem linha válida, todo posto ativo seria
 * "ausente da planilha" e iria para a lista de desativação. O caso típico é arquivo
 * errado — outro estado, outra exportação — e não uma rede que fechou todos os postos.
 */
public class PlanilhaSemPostosNoEscopoException extends RuntimeException {

    public PlanilhaSemPostosNoEscopoException(String message) {
        super(message);
    }
}
