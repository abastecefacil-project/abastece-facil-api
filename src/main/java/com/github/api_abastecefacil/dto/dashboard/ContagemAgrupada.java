package com.github.api_abastecefacil.dto.dashboard;

/**
 * Projeção das consultas nativas de agregação do resumo da Home: uma linha de
 * {@code GROUP BY}. Os getters casam com os aliases {@code chave} e {@code total} do SQL.
 *
 * <p>A chave é sempre {@code String}, inclusive quando é um perfil: em query nativa o enum
 * volta como o texto da coluna, e quem converte é o service.
 */
public interface ContagemAgrupada {

    String getChave();

    Long getTotal();
}
