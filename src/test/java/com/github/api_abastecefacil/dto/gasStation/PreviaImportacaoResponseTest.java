package com.github.api_abastecefacil.dto.gasStation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreviaImportacaoResponseTest {

    private static LinhaPlanilhaPosto linha(int numero) {
        return new LinhaPlanilhaPosto(numero, "1234567800019" + numero, "cnpj-" + numero, "Posto " + numero,
                null, null, "89201-250", "Rua " + numero + ", 10", "Centro", "Joinville", "SC", null, List.of());
    }

    private static ItemPlanoImportacao item(Long id, int numero, List<String> campos, boolean geocodificar) {
        return new ItemPlanoImportacao(id, "cnpj-" + numero, "Posto " + numero, "Joinville", campos, geocodificar,
                linha(numero));
    }

    @Test
    void de_ShouldPreserveCountsListsAndOrder() {
        OcorrenciaPlanilha erro = new OcorrenciaPlanilha(7, null, "CNPJ inválido");
        OcorrenciaPlanilha aviso = new OcorrenciaPlanilha(8, "cnpj-8", "CNPJ duplicado");
        PlanoImportacao plano = new PlanoImportacao(
                List.of(item(null, 1, List.of(), true), item(null, 2, List.of(), true)),
                List.of(item(10L, 3, List.of("Telefone", "CEP"), true)),
                List.of(item(11L, 4, List.of(), false)),
                List.of(new ItemPlanoImportacao(12L, "cnpj-d", "Fechado", "Itajaí", List.of(), false, null)),
                6, 40, 30, 25, List.of(erro), List.of(aviso));

        PreviaImportacaoResponse previa = PreviaImportacaoResponse.de(plano);

        assertThat(previa.totalLinhasLidas()).isEqualTo(40);
        assertThat(previa.totalNoEscopo()).isEqualTo(30);
        assertThat(previa.totalAtivosNoBanco()).isEqualTo(25);
        assertThat(previa.semAlteracao()).isEqualTo(6);
        assertThat(previa.inserir()).extracting(ItemPreviaImportacaoResponse::cnpj).containsExactly("cnpj-1", "cnpj-2");
        assertThat(previa.atualizar()).containsExactly(new ItemPreviaImportacaoResponse(10L, "cnpj-3", "Posto 3",
                "Joinville", List.of("Telefone", "CEP"), true));
        assertThat(previa.reativar()).containsExactly(new ItemPreviaImportacaoResponse(11L, "cnpj-4", "Posto 4",
                "Joinville", List.of(), false));
        assertThat(previa.desativar()).containsExactly(new ItemPreviaImportacaoResponse(12L, "cnpj-d", "Fechado",
                "Itajaí", List.of(), false));
        assertThat(previa.erros()).containsExactly(erro);
        assertThat(previa.avisos()).containsExactly(aviso);
    }

    /** Quebra se alguém acrescentar a linha da planilha ao item de resposta. */
    @Test
    void item_ShouldNotExposeSpreadsheetData() {
        assertThat(ItemPreviaImportacaoResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("id", "cnpj", "nome", "cidade", "camposAlterados", "requerGeocodificacao");
        assertThat(PreviaImportacaoResponse.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .doesNotContain(PlanoImportacao.class, LinhaPlanilhaPosto.class);
    }
}
