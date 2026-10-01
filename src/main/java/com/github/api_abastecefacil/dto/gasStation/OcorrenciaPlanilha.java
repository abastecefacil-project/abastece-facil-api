package com.github.api_abastecefacil.dto.gasStation;

/**
 * Aviso sobre uma linha da planilha que não impede a importação — hoje, CNPJ duplicado.
 *
 * @param linha    número da linha como o Excel a exibe (começa em 1)
 * @param cnpj     CNPJ formatado da linha, ou {@code null} se não houver um válido
 * @param mensagem descrição em pt-BR
 */
public record OcorrenciaPlanilha(
        int linha,
        String cnpj,
        String mensagem
) {
}
