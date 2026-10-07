package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * Desfecho de uma importação de postos. Existe na {@code CONCLUIDA}, na {@code FALHOU} e na
 * {@code CANCELADA}: numa importação interrompida ou cancelada, mostra o que chegou a ser
 * gravado.
 *
 * <p>As contagens só sobem quando a gravação deu certo. {@code semAlteracao} vem do plano.
 *
 * @param erros  os do plano (linhas com erro na planilha) mais os da execução — itens que
 *               não foram gravados, ou foram gravados sem o endereço
 * @param avisos os do plano mais os da execução — itens gravados com alguma ressalva
 */
public record ResumoImportacaoPostos(
        int inseridos,
        int atualizados,
        int reativados,
        int desativados,
        int semAlteracao,
        List<OcorrenciaPlanilha> erros,
        List<OcorrenciaPlanilha> avisos
) {
}
