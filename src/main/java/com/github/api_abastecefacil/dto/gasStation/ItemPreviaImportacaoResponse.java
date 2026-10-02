package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * Um posto na prévia da importação: o {@link ItemPlanoImportacao} <b>sem</b> o campo
 * {@code dados}.
 *
 * <p>A linha da planilha é interna: só a execução precisa dela, e ela recalcula o plano a
 * partir do arquivo reenviado. Expor {@code dados} dobraria a resposta sem servir a tela —
 * na primeira carga são cerca de 1.200 itens.
 */
public record ItemPreviaImportacaoResponse(
        Long id,
        String cnpj,
        String nome,
        String cidade,
        List<String> camposAlterados,
        boolean requerGeocodificacao
) {

    public static ItemPreviaImportacaoResponse de(ItemPlanoImportacao item) {
        return new ItemPreviaImportacaoResponse(item.id(), item.cnpj(), item.nome(), item.cidade(),
                item.camposAlterados(), item.requerGeocodificacao());
    }
}
