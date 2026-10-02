package com.github.api_abastecefacil.dto.gasStation;

import java.util.List;

/**
 * Um posto afetado pela importação. O mesmo tipo serve aos quatro grupos do
 * {@link PlanoImportacao}, para o frontend renderizá-los da mesma forma:
 *
 * <table>
 *   <tr><th>grupo</th><th>id</th><th>camposAlterados</th><th>requerGeocodificacao</th><th>dados</th></tr>
 *   <tr><td>inserir</td><td>{@code null}</td><td>vazio</td><td>{@code true}</td><td>linha</td></tr>
 *   <tr><td>atualizar / reativar</td><td>do banco</td><td>lista</td><td>calculado</td><td>linha</td></tr>
 *   <tr><td>desativar</td><td>do banco</td><td>vazio</td><td>{@code false}</td><td>{@code null}</td></tr>
 * </table>
 *
 * @param cnpj             sempre mascarado; o texto gravado só aparece se não tiver 14 dígitos
 * @param nome             Razão Social — o que vai para {@code GasStation.name}. Vem da
 *                         planilha, exceto em desativar, onde vem do banco
 * @param nomeFantasia     como o administrador reconhece o posto: o que ele terá depois da
 *                         importação. Em inserir, o da planilha; em atualizar e reativar, o
 *                         da planilha, ou o do banco quando a planilha não traz (nulo nunca
 *                         sobrescreve); em desativar, o do banco. Pode ser {@code null}
 * @param cidade           da planilha, exceto em desativar
 * @param camposAlterados  nomes das colunas da planilha, em pt-BR, na ordem fixa do plano
 * @param dados            a linha da planilha que a execução vai gravar
 */
public record ItemPlanoImportacao(
        Long id,
        String cnpj,
        String nome,
        String nomeFantasia,
        String cidade,
        List<String> camposAlterados,
        boolean requerGeocodificacao,
        LinhaPlanilhaPosto dados
) {
}
