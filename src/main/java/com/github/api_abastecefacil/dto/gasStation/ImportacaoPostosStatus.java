package com.github.api_abastecefacil.dto.gasStation;

import com.github.api_abastecefacil.model.StatusImportacao;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Situação de uma importação de postos em segundo plano. Imutável: cada avanço produz um
 * novo valor no {@code RegistroImportacoesPostos}.
 *
 * @param total       itens de inserir, atualizar, reativar e desativar do plano
 * @param processados itens já tentados, com ou sem erro
 * @param concluidaEm {@code null} enquanto {@code EM_ANDAMENTO}
 * @param mensagem    {@code null} enquanto {@code EM_ANDAMENTO}
 * @param resumo      {@code null} enquanto {@code EM_ANDAMENTO}
 */
public record ImportacaoPostosStatus(
        UUID id,
        StatusImportacao status,
        int total,
        int processados,
        LocalDateTime iniciadaEm,
        LocalDateTime concluidaEm,
        String mensagem,
        ResumoImportacaoPostos resumo
) {

    public ImportacaoPostosStatus comMaisUmProcessado() {
        return new ImportacaoPostosStatus(id, status, total, processados + 1, iniciadaEm, concluidaEm,
                mensagem, resumo);
    }

    public ImportacaoPostosStatus finalizada(StatusImportacao novoStatus, LocalDateTime em, String novaMensagem,
                                             ResumoImportacaoPostos novoResumo) {
        return new ImportacaoPostosStatus(id, novoStatus, total, processados, iniciadaEm, em, novaMensagem,
                novoResumo);
    }
}
