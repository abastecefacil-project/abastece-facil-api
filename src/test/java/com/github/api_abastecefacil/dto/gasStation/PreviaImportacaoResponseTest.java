package com.github.api_abastecefacil.dto.gasStation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreviaImportacaoResponseTest {

    private static LinhaPlanilhaPosto linha(int numero, String nomeFantasia) {
        return new LinhaPlanilhaPosto(numero, "1234567800019" + numero, "cnpj-" + numero, "Posto " + numero,
                nomeFantasia, null, "89201-250", "Rua " + numero + ", 10", "Centro", "Joinville", "SC", null,
                List.of());
    }

    private static ItemPlanoImportacao item(Long id, int numero, String nomeFantasia, List<String> campos,
                                            boolean geocodificar) {
        return new ItemPlanoImportacao(id, "cnpj-" + numero, "Posto " + numero + " LTDA", nomeFantasia, "Joinville",
                campos, geocodificar, linha(numero, nomeFantasia));
    }

    @Test
    void de_ShouldPreserveCountsListsAndOrder() {
        OcorrenciaPlanilha erro = new OcorrenciaPlanilha(7, null, "CNPJ inválido");
        OcorrenciaPlanilha aviso = new OcorrenciaPlanilha(8, "cnpj-8", "CNPJ duplicado");
        PlanoImportacao plano = new PlanoImportacao(
                List.of(item(null, 1, "POSTO UM", List.of(), true), item(null, 2, "POSTO DOIS", List.of(), true)),
                List.of(item(10L, 3, "POSTO TRES", List.of("Telefone", "CEP"), true)),
                List.of(item(11L, 4, "POSTO QUATRO", List.of(), false)),
                List.of(new ItemPlanoImportacao(12L, "cnpj-d", "FECHADO LTDA", "FECHADO", "Itajaí", List.of(), false,
                        null)),
                6, 40, 30, 25, List.of(erro), List.of(aviso));

        PreviaImportacaoResponse previa = PreviaImportacaoResponse.de(plano);

        assertThat(previa.totalLinhasLidas()).isEqualTo(40);
        assertThat(previa.totalNoEscopo()).isEqualTo(30);
        assertThat(previa.totalAtivosNoBanco()).isEqualTo(25);
        assertThat(previa.semAlteracao()).isEqualTo(6);
        assertThat(previa.inserir()).extracting(ItemPreviaImportacaoResponse::cnpj).containsExactly("cnpj-1", "cnpj-2");
        assertThat(previa.atualizar()).containsExactly(new ItemPreviaImportacaoResponse(10L, "cnpj-3", "Posto 3 LTDA",
                "POSTO TRES", "Joinville", List.of("Telefone", "CEP"), true));
        assertThat(previa.reativar()).containsExactly(new ItemPreviaImportacaoResponse(11L, "cnpj-4", "Posto 4 LTDA",
                "POSTO QUATRO", "Joinville", List.of(), false));
        assertThat(previa.desativar()).containsExactly(new ItemPreviaImportacaoResponse(12L, "cnpj-d", "FECHADO LTDA",
                "FECHADO", "Itajaí", List.of(), false));
        assertThat(previa.erros()).containsExactly(erro);
        assertThat(previa.avisos()).containsExactly(aviso);
    }

    @Test
    void de_ShouldCarryNomeFantasiaInAllFourGroups() {
        PlanoImportacao plano = new PlanoImportacao(
                List.of(item(null, 1, "POSTO ZANDONA 21", List.of(), true)),
                List.of(item(10L, 2, "POSTO ATUALIZADO", List.of("Telefone"), false)),
                List.of(item(11L, 3, "POSTO REATIVADO", List.of(), false)),
                List.of(new ItemPlanoImportacao(12L, "cnpj-d", "FECHADO LTDA", "POSTO DO BANCO", "Itajaí",
                        List.of(), false, null)),
                0, 4, 4, 4, List.of(), List.of());

        PreviaImportacaoResponse previa = PreviaImportacaoResponse.de(plano);

        assertThat(previa.inserir()).extracting(ItemPreviaImportacaoResponse::nomeFantasia)
                .containsExactly("POSTO ZANDONA 21");
        assertThat(previa.atualizar()).extracting(ItemPreviaImportacaoResponse::nomeFantasia)
                .containsExactly("POSTO ATUALIZADO");
        assertThat(previa.reativar()).extracting(ItemPreviaImportacaoResponse::nomeFantasia)
                .containsExactly("POSTO REATIVADO");
        assertThat(previa.desativar()).extracting(ItemPreviaImportacaoResponse::nomeFantasia)
                .containsExactly("POSTO DO BANCO");
    }

    /** Na planilha real há postos sem nome fantasia; o campo vai nulo, sem cair para a razão social. */
    @Test
    void de_ShouldKeepNomeFantasiaNull_WhenStationHasNone() {
        PlanoImportacao plano = new PlanoImportacao(
                List.of(item(null, 1, null, List.of(), true)),
                List.of(item(10L, 2, null, List.of("Telefone"), false)),
                List.of(item(11L, 3, null, List.of(), false)),
                List.of(new ItemPlanoImportacao(12L, "cnpj-d", "FECHADO LTDA", null, "Itajaí", List.of(), false, null)),
                0, 4, 4, 4, List.of(), List.of());

        PreviaImportacaoResponse previa = PreviaImportacaoResponse.de(plano);

        assertThat(previa.inserir()).singleElement().satisfies(item -> {
            assertThat(item.nomeFantasia()).isNull();
            assertThat(item.nome()).isEqualTo("Posto 1 LTDA");
        });
        assertThat(previa.atualizar()).singleElement().extracting(ItemPreviaImportacaoResponse::nomeFantasia).isNull();
        assertThat(previa.reativar()).singleElement().extracting(ItemPreviaImportacaoResponse::nomeFantasia).isNull();
        assertThat(previa.desativar()).singleElement().extracting(ItemPreviaImportacaoResponse::nomeFantasia).isNull();
    }

    /** Quebra se alguém acrescentar a linha da planilha ao item de resposta. */
    @Test
    void item_ShouldNotExposeSpreadsheetData() {
        assertThat(ItemPreviaImportacaoResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("id", "cnpj", "nome", "nomeFantasia", "cidade", "camposAlterados",
                        "requerGeocodificacao")
                .doesNotContain("dados");
        assertThat(ItemPreviaImportacaoResponse.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .doesNotContain(LinhaPlanilhaPosto.class);
        assertThat(PreviaImportacaoResponse.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .doesNotContain(PlanoImportacao.class, LinhaPlanilhaPosto.class);
    }
}
