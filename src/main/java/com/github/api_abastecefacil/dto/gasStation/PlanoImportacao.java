package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * O que a importação da planilha faria no cadastro de postos. Calculado sem gravar nada;
 * serve à prévia e à execução.
 *
 * @param semAlteracao       postos ativos presentes na planilha sem nenhuma diferença
 * @param totalLinhasLidas   repassado do leitor
 * @param totalNoEscopo      repassado do leitor
 * @param totalAtivosNoBanco postos com {@code isActive = true} antes da importação, todos —
 *                           inclusive os de CNPJ duplicado ou inválido. É a base para
 *                           mostrar a desativação em proporção
 * @param erros              uma ocorrência por mensagem de cada linha com erro
 * @param avisos             os do leitor, mais os de CNPJ duplicado no banco
 */
public record PlanoImportacao(
        List<ItemPlanoImportacao> inserir,
        List<ItemPlanoImportacao> atualizar,
        List<ItemPlanoImportacao> reativar,
        List<ItemPlanoImportacao> desativar,
        int semAlteracao,
        int totalLinhasLidas,
        int totalNoEscopo,
        int totalAtivosNoBanco,
        List<OcorrenciaPlanilha> erros,
        List<OcorrenciaPlanilha> avisos
) {
}
