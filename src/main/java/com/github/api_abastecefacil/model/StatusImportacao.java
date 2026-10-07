package com.github.api_abastecefacil.model;

/** Situação de uma importação de postos. Não é persistido: o registro é em memória. */
public enum StatusImportacao {
    EM_ANDAMENTO,
    CONCLUIDA,
    FALHOU,
    /** Interrompida a pedido de um administrador. Ver {@code ExecutorImportacaoPostos}. */
    CANCELADA
}
