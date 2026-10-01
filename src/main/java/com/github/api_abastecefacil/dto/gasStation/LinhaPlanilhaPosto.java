package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * Uma linha da planilha de postos, já normalizada pelo {@code NormalizadorPlanilhaPostos}.
 *
 * <p>{@code cnpjDigitos} e {@code cnpj} vêm preenchidos sempre que o CNPJ da linha for
 * válido, <b>inclusive quando a linha tem erro em outro campo</b>. A sincronização
 * depende disso: um posto cuja linha veio com defeito continua presente na planilha e
 * não pode ser tratado como ausente.
 *
 * @param numeroLinha número da linha como o Excel a exibe (começa em 1)
 * @param erros       mensagens em pt-BR, com o nome da coluna da planilha; vazia se a
 *                    linha é válida
 */
public record LinhaPlanilhaPosto(
        int numeroLinha,
        String cnpjDigitos,
        String cnpj,
        String name,
        String fantasyName,
        String phone,
        String cep,
        String address,
        String district,
        String city,
        String state,
        String businessHours,
        List<String> erros
) {

    public boolean valida() {
        return erros.isEmpty();
    }
}
