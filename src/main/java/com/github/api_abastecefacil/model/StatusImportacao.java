package com.github.api_abastecefacil.model;

/** Situação de uma importação de postos. Não é persistido: o registro é em memória. */
public enum StatusImportacao {
    EM_ANDAMENTO,
    CONCLUIDA,
    FALHOU
}
