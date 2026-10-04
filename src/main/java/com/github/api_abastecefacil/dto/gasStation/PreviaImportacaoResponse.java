package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * Resposta de {@code POST /api/gas-stations/import/preview}: o que a importação faria, sem
 * gravar nada.
 *
 * <p>É o {@link PlanoImportacao} com os itens trocados por {@link ItemPreviaImportacaoResponse},
 * que não carrega a linha da planilha. O plano não é exposto direto para o contrato HTTP não
 * mudar toda vez que a execução precisar de um campo interno novo.
 */
public record PreviaImportacaoResponse(
        int totalLinhasLidas,
        int totalNoEscopo,
        int totalAtivosNoBanco,
        int semAlteracao,
        List<ItemPreviaImportacaoResponse> inserir,
        List<ItemPreviaImportacaoResponse> atualizar,
        List<ItemPreviaImportacaoResponse> reativar,
        List<ItemPreviaImportacaoResponse> desativar,
        List<OcorrenciaPlanilha> erros,
        List<OcorrenciaPlanilha> avisos
) {

    public static PreviaImportacaoResponse de(PlanoImportacao plano) {
        return new PreviaImportacaoResponse(
                plano.totalLinhasLidas(),
                plano.totalNoEscopo(),
                plano.totalAtivosNoBanco(),
                plano.semAlteracao(),
                itens(plano.inserir()),
                itens(plano.atualizar()),
                itens(plano.reativar()),
                itens(plano.desativar()),
                plano.erros(),
                plano.avisos());
    }

    private static List<ItemPreviaImportacaoResponse> itens(List<ItemPlanoImportacao> itens) {
        return itens.stream().map(ItemPreviaImportacaoResponse::de).toList();
    }
}
