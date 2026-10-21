package com.github.api_abastecefacil.dto.dashboard;

/**
 * Projeção do ranking de cidades. Os getters casam com os aliases {@code cidade}, {@code uf}
 * e {@code total} do SQL.
 */
public interface ContagemCidade {

    String getCidade();

    String getUf();

    Long getTotal();
}
