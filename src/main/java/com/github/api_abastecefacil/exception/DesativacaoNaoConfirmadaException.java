package com.github.api_abastecefacil.exception;

/**
 * O plano da importação de postos desativa postos, e o administrador não informou a ciência
 * dada na prévia. Sem ela, um arquivo incompleto desativaria postos válidos sem que ninguém
 * tivesse visto a lista. Nada foi gravado e nenhuma importação foi reservada.
 */
public class DesativacaoNaoConfirmadaException extends RuntimeException {

    public DesativacaoNaoConfirmadaException(String message) {
        super(message);
    }
}
