package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * Resultado da leitura da planilha de postos, antes de qualquer acesso ao banco.
 *
 * @param totalLinhasLidas linhas de dados depois do cabeçalho — sem o preâmbulo, sem o
 *                         próprio cabeçalho e sem linhas totalmente vazias. Inclui as
 *                         linhas fora do escopo de UF e tipo.
 * @param totalNoEscopo    das lidas, as que passaram no filtro de UF e tipo
 *                         ({@code importacao-postos.*})
 * @param linhasValidas    linhas no escopo, sem erro e sem CNPJ repetido — a primeira
 *                         ocorrência válida de cada CNPJ
 * @param linhasComErro    linhas no escopo com ao menos um erro
 * @param avisos           ocorrências que não impedem a importação, como CNPJ duplicado
 */
public record ResultadoLeituraPlanilha(
        int totalLinhasLidas,
        int totalNoEscopo,
        List<LinhaPlanilhaPosto> linhasValidas,
        List<LinhaPlanilhaPosto> linhasComErro,
        List<OcorrenciaPlanilha> avisos
) {
}
