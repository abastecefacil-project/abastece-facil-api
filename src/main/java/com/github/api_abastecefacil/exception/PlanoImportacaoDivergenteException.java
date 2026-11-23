package com.github.api_abastecefacil.exception;

/**
 * A quantidade de desativações que o administrador confirmou na prévia não é a do plano
 * recalculado no início da importação: o banco ou o arquivo mudou entre a prévia e o clique.
 * A ciência dada não descreve mais o que seria feito. Nada foi gravado e nenhuma importação
 * foi reservada.
 */
public class PlanoImportacaoDivergenteException extends RuntimeException {

    public PlanoImportacaoDivergenteException(String message) {
        super(message);
    }
}
