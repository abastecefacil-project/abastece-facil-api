package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ItemPlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.dto.gasStation.OcorrenciaPlanilha;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.ResultadoLeituraPlanilha;
import com.github.api_abastecefacil.exception.PlanilhaSemPostosNoEscopoException;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanejadorImportacaoPostosTest {

    private static final String CNPJ_DIGITOS = "12345678000126";
    private static final String CNPJ = "12.345.678/0001-26";

    @Mock
    private GasStationRepository gasStationRepository;

    @InjectMocks
    private PlanejadorImportacaoPostos planejador;

    // ---------- classificação ----------

    @Test
    void planejar_ShouldInsert_WhenCnpjIsNotInDatabase() {
        banco();
        LinhaPlanilhaPosto linha = linha().build();

        PlanoImportacao plano = planejador.planejar(leitura(linha));

        assertThat(plano.inserir()).containsExactly(
                new ItemPlanoImportacao(null, CNPJ, "POSTO CENTRAL LTDA", "JOINVILLE", List.of(), true, linha));
        assertThat(plano.atualizar()).isEmpty();
        assertThat(plano.semAlteracao()).isZero();
    }

    @Test
    void planejar_ShouldReactivateAndListChanges_WhenInactiveStationIsPresent() {
        banco(posto(7L, CNPJ, false).setPhone("(47) 3422-1234"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.reativar()).hasSize(1);
        ItemPlanoImportacao item = plano.reativar().get(0);
        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.camposAlterados()).containsExactly("Telefone");
        assertThat(item.requerGeocodificacao()).isFalse();
        assertThat(item.dados()).isNotNull();
        assertThat(plano.atualizar()).isEmpty();
        assertThat(plano.desativar()).isEmpty();
    }

    @Test
    void planejar_ShouldReactivateWithoutChanges_WhenInactiveStationIsIdentical() {
        banco(posto(7L, CNPJ, false));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.reativar()).singleElement()
                .satisfies(item -> assertThat(item.camposAlterados()).isEmpty());
        assertThat(plano.semAlteracao()).isZero();
    }

    @Test
    void planejar_ShouldUpdateWithoutGeocoding_WhenOnlyPhoneChanged() {
        banco(posto(1L, CNPJ, true).setPhone("(47) 3422-1234"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.atualizar()).singleElement().satisfies(item -> {
            assertThat(item.camposAlterados()).containsExactly("Telefone");
            assertThat(item.requerGeocodificacao()).isFalse();
        });
    }

    @Test
    void planejar_ShouldRequireGeocoding_WhenCepDigitsChanged() {
        banco(posto(1L, CNPJ, true).setCep("89226-526"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.atualizar()).singleElement().satisfies(item -> {
            assertThat(item.camposAlterados()).containsExactly("CEP");
            assertThat(item.requerGeocodificacao()).isTrue();
        });
    }

    @Test
    void planejar_ShouldRequireGeocoding_WhenDistrictReallyChanged() {
        banco(posto(1L, CNPJ, true).setDistrict("JARDIM PARAISO"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.atualizar()).singleElement().satisfies(item -> {
            assertThat(item.camposAlterados()).containsExactly("Bairro");
            assertThat(item.requerGeocodificacao()).isTrue();
        });
    }

    @Test
    void planejar_ShouldBeUnchanged_WhenCepDiffersOnlyByHyphen() {
        banco(posto(1L, CNPJ, true).setCep("89226120"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().cep("89226-120").build()));

        assertThat(plano.semAlteracao()).isEqualTo(1);
        assertThat(plano.atualizar()).isEmpty();
    }

    @Test
    void planejar_ShouldUpdateTextWithoutGeocoding_WhenAddressDiffersOnlyByCase() {
        banco(posto(1L, CNPJ, true).setAddress("Rua Dom Gregorio Warmeling, 1117"));

        PlanoImportacao plano = planejador.planejar(leitura(
                linha().address("RUA DOM GREGORIO WARMELING, 1117").build()));

        assertThat(plano.atualizar()).singleElement().satisfies(item -> {
            assertThat(item.camposAlterados()).containsExactly("Endereço");
            assertThat(item.requerGeocodificacao()).isFalse();
        });
    }

    @Test
    void planejar_ShouldUpdateTextWithoutGeocoding_WhenDistrictDiffersOnlyByAccentAndSpaces() {
        banco(posto(1L, CNPJ, true).setDistrict("Jardim  Paraíso"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().district("JARDIM PARAISO").build()));

        assertThat(plano.atualizar()).singleElement().satisfies(item -> {
            assertThat(item.camposAlterados()).containsExactly("Bairro");
            assertThat(item.requerGeocodificacao()).isFalse();
        });
    }

    @Test
    void planejar_ShouldMatchUnmaskedCnpj_AndListCnpjAsChanged() {
        banco(posto(1L, CNPJ_DIGITOS, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.inserir()).isEmpty();
        assertThat(plano.desativar()).isEmpty();
        assertThat(plano.atualizar()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(1L);
            assertThat(item.cnpj()).isEqualTo(CNPJ);
            assertThat(item.camposAlterados()).containsExactly("CNPJ");
            assertThat(item.requerGeocodificacao()).isFalse();
        });
    }

    @Test
    void planejar_ShouldListChangedFields_InSpreadsheetOrder() {
        banco(posto(1L, CNPJ_DIGITOS, true)
                .setBusinessHours("08:00 - 18:00")
                .setCity("ARAQUARI")
                .setName("OUTRA RAZAO")
                .setPhone("(47) 3422-1234"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.atualizar().get(0).camposAlterados())
                .containsExactly("Razão Social", "Telefone", "Cidade", "Horário", "CNPJ");
    }

    // ---------- nulo na planilha não sobrescreve ----------

    @Test
    void planejar_ShouldBeUnchanged_WhenBusinessHoursIsNullInSpreadsheet() {
        banco(posto(1L, CNPJ, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().businessHours(null).build()));

        assertThat(plano.semAlteracao()).isEqualTo(1);
    }

    @Test
    void planejar_ShouldBeUnchanged_WhenFantasyNameIsNullInSpreadsheet() {
        banco(posto(1L, CNPJ, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().fantasyName(null).build()));

        assertThat(plano.semAlteracao()).isEqualTo(1);
    }

    @Test
    void planejar_ShouldBeUnchanged_WhenPhoneIsNullInSpreadsheet() {
        banco(posto(1L, CNPJ, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().phone(null).build()));

        assertThat(plano.semAlteracao()).isEqualTo(1);
    }

    @Test
    void planejar_ShouldUpdate_WhenPhoneIsDifferentAndNotNull() {
        banco(posto(1L, CNPJ, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().phone("(47) 3422-1234").build()));

        assertThat(plano.atualizar()).singleElement()
                .satisfies(item -> assertThat(item.camposAlterados()).containsExactly("Telefone"));
    }

    // ---------- desativação ----------

    @Test
    void planejar_ShouldDeactivate_WhenActiveStationIsAbsent() {
        banco(posto(1L, CNPJ, true), posto(2L, "98.765.432/0001-98", true).setName("POSTO AUSENTE"));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.desativar()).containsExactly(
                new ItemPlanoImportacao(2L, "98.765.432/0001-98", "POSTO AUSENTE", "JOINVILLE", List.of(), false, null));
    }

    @Test
    void planejar_ShouldNotDeactivate_WhenInactiveStationIsAbsent() {
        banco(posto(1L, CNPJ, true), posto(2L, "98.765.432/0001-98", false));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.desativar()).isEmpty();
    }

    @Test
    void planejar_ShouldNotDeactivate_WhenRowWithErrorHasValidCnpj() {
        banco(posto(1L, CNPJ, true), posto(2L, "98.765.432/0001-98", true));
        LinhaPlanilhaPosto comErro = linha().numero(3).cnpj("98765432000198", "98.765.432/0001-98")
                .district(null).erros("Bairro vazio").build();

        PlanoImportacao plano = planejador.planejar(leitura(List.of(linha().build()), List.of(comErro), List.of()));

        assertThat(plano.desativar()).isEmpty();
        assertThat(plano.atualizar()).isEmpty();
        assertThat(plano.reativar()).isEmpty();
    }

    @Test
    void planejar_ShouldDeactivate_WhenDatabaseCnpjIsInvalid() {
        banco(posto(1L, CNPJ, true), posto(2L, "123", true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.desativar()).extracting(ItemPlanoImportacao::id, ItemPlanoImportacao::cnpj)
                .containsExactly(tuple(2L, "123"));
    }

    @Test
    void planejar_ShouldKeepMaskedAndDeactivateOthers_WhenCnpjIsDuplicatedInDatabase() {
        banco(posto(1L, CNPJ_DIGITOS, true), posto(5L, CNPJ, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().numero(8).build()));

        assertThat(plano.semAlteracao()).isEqualTo(1);
        assertThat(plano.desativar()).extracting(ItemPlanoImportacao::id).containsExactly(1L);
        assertThat(plano.avisos()).containsExactly(new OcorrenciaPlanilha(8, CNPJ,
                "CNPJ cadastrado em 2 postos; mantido o id 5, os demais ativos serão desativados"));
    }

    @Test
    void planejar_ShouldPreferActive_WhenNoDuplicateIsMasked() {
        banco(posto(1L, "12345678/000126", false), posto(5L, CNPJ_DIGITOS, true));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.atualizar()).extracting(ItemPlanoImportacao::id).containsExactly(5L);
        assertThat(plano.reativar()).isEmpty();
        assertThat(plano.desativar()).isEmpty();
    }

    @Test
    void planejar_ShouldCountAllActiveStations_IncludingDuplicatesAndInvalid() {
        banco(posto(1L, CNPJ_DIGITOS, true), posto(2L, CNPJ, true), posto(3L, "123", true),
                posto(4L, "98.765.432/0001-98", false));

        PlanoImportacao plano = planejador.planejar(leitura(linha().build()));

        assertThat(plano.totalAtivosNoBanco()).isEqualTo(3);
    }

    // ---------- erros, avisos e totais ----------

    @Test
    void planejar_ShouldConvertErrorRows_IntoOneOccurrencePerMessage() {
        banco();
        LinhaPlanilhaPosto duasMensagens = linha().numero(4).erros("Bairro vazio", "Cidade vazia").build();
        LinhaPlanilhaPosto semCnpj = linha().numero(5).cnpj(null, null).erros("CNPJ inválido (deve ter 14 dígitos)").build();

        PlanoImportacao plano = planejador.planejar(leitura(
                List.of(linha().cnpj("98765432000198", "98.765.432/0001-98").build()),
                List.of(duasMensagens, semCnpj), List.of()));

        assertThat(plano.erros()).containsExactly(
                new OcorrenciaPlanilha(4, CNPJ, "Bairro vazio"),
                new OcorrenciaPlanilha(4, CNPJ, "Cidade vazia"),
                new OcorrenciaPlanilha(5, null, "CNPJ inválido (deve ter 14 dígitos)"));
    }

    @Test
    void planejar_ShouldPassThroughReaderWarningsAndTotals() {
        banco();
        OcorrenciaPlanilha aviso = new OcorrenciaPlanilha(9, CNPJ, "CNPJ duplicado; mantida a linha 2");

        PlanoImportacao plano = planejador.planejar(leitura(List.of(linha().build()), List.of(), List.of(aviso)));

        assertThat(plano.avisos()).containsExactly(aviso);
        assertThat(plano.totalLinhasLidas()).isEqualTo(100);
        assertThat(plano.totalNoEscopo()).isEqualTo(10);
    }

    @Test
    void planejar_ShouldThrowBeforeQueryingDatabase_WhenNoValidRowsInScope() {
        LinhaPlanilhaPosto comErro = linha().erros("Bairro vazio").build();
        ResultadoLeituraPlanilha semValidas = leitura(List.of(), List.of(comErro), List.of());

        PlanilhaSemPostosNoEscopoException ex = assertThrows(PlanilhaSemPostosNoEscopoException.class,
                () -> planejador.planejar(semValidas));

        assertThat(ex.getMessage()).isEqualTo(
                "Nenhuma linha válida no escopo da importação (100 lidas, 10 no escopo, 1 com erro). "
                        + "Nenhum posto foi alterado.");
        verifyNoInteractions(gasStationRepository);
    }

    // ---------- apoio ----------

    private void banco(GasStation... postos) {
        when(gasStationRepository.findAll()).thenReturn(Arrays.asList(postos));
    }

    /** Idêntico à {@link #linha()} padrão, para que a diferença de cada teste seja só a que ele declara. */
    private static GasStation posto(Long id, String cnpj, boolean ativo) {
        return new GasStation()
                .setId(id)
                .setCnpj(cnpj)
                .setName("POSTO CENTRAL LTDA")
                .setFantasyName("POSTO CENTRAL")
                .setPhone("(47) 99623-5372")
                .setCep("89226-120")
                .setAddress("RUA RUDOLFO FINDER, 225")
                .setDistrict("AVENTUREIRO")
                .setCity("JOINVILLE")
                .setState("SC")
                .setBusinessHours("06:00 - 22:00")
                .setLatitude(new BigDecimal("-26.25176380"))
                .setLongitude(new BigDecimal("-48.82228540"))
                .setActive(ativo);
    }

    private static ResultadoLeituraPlanilha leitura(LinhaPlanilhaPosto... validas) {
        return leitura(List.of(validas), List.of(), List.of());
    }

    private static ResultadoLeituraPlanilha leitura(List<LinhaPlanilhaPosto> validas,
                                                    List<LinhaPlanilhaPosto> comErro,
                                                    List<OcorrenciaPlanilha> avisos) {
        return new ResultadoLeituraPlanilha(100, 10, validas, comErro, avisos);
    }

    private static LinhaBuilder linha() {
        return new LinhaBuilder();
    }

    /** O record é imutável; o builder deixa cada teste declarar só o campo que difere. */
    private static final class LinhaBuilder {
        private int numero = 2;
        private String cnpjDigitos = CNPJ_DIGITOS;
        private String cnpj = CNPJ;
        private final String name = "POSTO CENTRAL LTDA";
        private String fantasyName = "POSTO CENTRAL";
        private String phone = "(47) 99623-5372";
        private String cep = "89226-120";
        private String address = "RUA RUDOLFO FINDER, 225";
        private String district = "AVENTUREIRO";
        private final String city = "JOINVILLE";
        private final String state = "SC";
        private String businessHours = "06:00 - 22:00";
        private List<String> erros = List.of();

        LinhaBuilder numero(int numero) { this.numero = numero; return this; }
        LinhaBuilder cnpj(String digitos, String mascarado) { this.cnpjDigitos = digitos; this.cnpj = mascarado; return this; }
        LinhaBuilder fantasyName(String valor) { this.fantasyName = valor; return this; }
        LinhaBuilder phone(String valor) { this.phone = valor; return this; }
        LinhaBuilder cep(String valor) { this.cep = valor; return this; }
        LinhaBuilder address(String valor) { this.address = valor; return this; }
        LinhaBuilder district(String valor) { this.district = valor; return this; }
        LinhaBuilder businessHours(String valor) { this.businessHours = valor; return this; }
        LinhaBuilder erros(String... mensagens) { this.erros = List.of(mensagens); return this; }

        LinhaPlanilhaPosto build() {
            return new LinhaPlanilhaPosto(numero, cnpjDigitos, cnpj, name, fantasyName, phone, cep, address,
                    district, city, state, businessHours, erros);
        }
    }
}
