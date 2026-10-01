package com.github.api_abastecefacil.dto.gasStation;

/**
 * Erro ou aviso sobre uma linha da planilha.
 *
 * <p>Também carrega os erros e avisos da execução da importação. Os itens de desativar não
 * vêm da planilha — são postos do banco ausentes dela — e saem com {@code linha = 0}
 * ({@code PlanilhaPostosConstants.LINHA_FORA_DA_PLANILHA}): o Excel numera a partir de 1,
 * então 0 nunca é uma linha real. O frontend exibe "—".
 *
 * @param linha    número da linha como o Excel a exibe (começa em 1), ou 0 para item que
 *                 não vem da planilha
 * @param cnpj     CNPJ formatado da linha, ou {@code null} se não houver um válido
 * @param mensagem descrição em pt-BR
 */
public record OcorrenciaPlanilha(
        int linha,
        String cnpj,
        String mensagem
) {
}
