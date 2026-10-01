package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.dto.gasStation.OcorrenciaPlanilha;
import com.github.api_abastecefacil.dto.gasStation.ResultadoLeituraPlanilha;
import com.github.api_abastecefacil.exception.PlanilhaInvalidaException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.CABECALHO_NAO_ENCONTRADO_MESSAGE;
import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.PLANILHA_INVALIDA_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * As planilhas são geradas em memória com POI a cada teste — nenhum binário versionado.
 */
class LeitorPlanilhaPostosTest {

    private static final List<String> CABECALHO = List.of(
            "Nome Fantasia", "Razão Social", "CNPJ", "CEP", "Logradouro", "Endereço", "Número",
            "Bairro", "UF", "Cidade", "Telefone", "Tipo", "Horario Funcionamento", "Bandeira");

    private final LeitorPlanilhaPostos leitor = new LeitorPlanilhaPostos(List.of("SC"), List.of("POSTO"));

    // ---------- mapeamento ----------

    @Test
    void ler_ShouldMapAllFields_WhenRowIsValid() {
        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(posto())));

        assertThat(resultado.linhasValidas()).hasSize(1);
        LinhaPlanilhaPosto linha = resultado.linhasValidas().get(0);
        assertThat(linha.numeroLinha()).isEqualTo(2);
        assertThat(linha.cnpjDigitos()).isEqualTo("12345678000126");
        assertThat(linha.cnpj()).isEqualTo("12.345.678/0001-26");
        assertThat(linha.name()).isEqualTo("POSTO CENTRAL LTDA");
        assertThat(linha.fantasyName()).isEqualTo("POSTO CENTRAL");
        assertThat(linha.phone()).isEqualTo("(47) 99623-5372");
        assertThat(linha.cep()).isEqualTo("89226-120");
        assertThat(linha.address()).isEqualTo("RUA RUDOLFO FINDER, 225");
        assertThat(linha.district()).isEqualTo("AVENTUREIRO");
        assertThat(linha.city()).isEqualTo("JOINVILLE");
        assertThat(linha.state()).isEqualTo("SC");
        assertThat(linha.businessHours()).isEqualTo("06:00 - 22:00");
        assertThat(linha.erros()).isEmpty();
    }

    @Test
    void ler_ShouldFindHeader_WhenPreambleRowsComeBeforeIt() {
        List<List<Object>> preambulo = List.of(
                List.of("Relação de postos credenciados"),
                List.of(),
                List.of("Gerado em", "01/09/2026"),
                List.of("Nome Fantasia", "CNPJ"));

        ResultadoLeituraPlanilha resultado = ler(planilha(preambulo, CABECALHO, List.of(posto())));

        // Cabeçalho na linha 5 do Excel, dado na 6. O preâmbulo não conta como lido.
        assertThat(resultado.totalLinhasLidas()).isEqualTo(1);
        assertThat(resultado.linhasValidas()).extracting(LinhaPlanilhaPosto::numeroLinha).containsExactly(6);
    }

    @Test
    void ler_ShouldMapByName_WhenColumnsAreInDifferentOrder() {
        List<String> invertido = new ArrayList<>(CABECALHO);
        Collections.reverse(invertido);

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), invertido, List.of(posto())));

        LinhaPlanilhaPosto linha = resultado.linhasValidas().get(0);
        assertThat(linha.name()).isEqualTo("POSTO CENTRAL LTDA");
        assertThat(linha.fantasyName()).isEqualTo("POSTO CENTRAL");
        assertThat(linha.district()).isEqualTo("AVENTUREIRO");
        assertThat(linha.city()).isEqualTo("JOINVILLE");
    }

    @Test
    void ler_ShouldMatchHeader_IgnoringCaseAccentsAndOuterSpaces() {
        List<String> cabecalho = CABECALHO.stream()
                .map(nome -> "  " + nome.toUpperCase().replace("Ã", "A").replace("Ç", "C").replace("Ú", "U") + " ")
                .toList();
        Map<String, Object> posto = new LinkedHashMap<>();
        posto().forEach((nome, valor) -> posto.put(cabecalho.get(CABECALHO.indexOf(nome)), valor));

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), cabecalho, List.of(posto)));

        assertThat(resultado.linhasValidas()).hasSize(1);
    }

    @Test
    void ler_ShouldReadNumericCellsWithoutScientificNotation_WhenCepAndNumberAreNumbers() {
        Map<String, Object> posto = posto();
        posto.put("CEP", 89226120d);
        posto.put("Número", 1420d);
        posto.put("Telefone", 47996235372d);

        LinhaPlanilhaPosto linha = ler(planilha(List.of(), CABECALHO, List.of(posto))).linhasValidas().get(0);

        assertThat(linha.cep()).isEqualTo("89226-120");
        assertThat(linha.address()).isEqualTo("RUA RUDOLFO FINDER, 1420");
        assertThat(linha.phone()).isEqualTo("(47) 99623-5372");
    }

    // ---------- arquivo inválido ----------

    @Test
    void ler_ShouldThrowCitingColumnAndHeaderRow_WhenRequiredColumnIsMissing() {
        List<String> semBairro = CABECALHO.stream().filter(nome -> !nome.equals("Bairro")).toList();
        List<List<Object>> preambulo = List.of(List.of("Relação"), List.of(), List.of(), List.of());

        PlanilhaInvalidaException ex = assertThrows(PlanilhaInvalidaException.class,
                () -> ler(planilha(preambulo, semBairro, List.of(posto()))));

        assertThat(ex.getMessage())
                .isEqualTo("Colunas obrigatórias ausentes no cabeçalho da linha 5: Bairro.");
    }

    @Test
    void ler_ShouldThrowHeaderNotFound_WhenNoRowLooksLikeHeader() {
        List<List<Object>> soPreambulo = List.of(List.of("Relação de postos"), List.of("Nome Fantasia", "CNPJ"));

        PlanilhaInvalidaException ex = assertThrows(PlanilhaInvalidaException.class,
                () -> ler(planilha(soPreambulo, List.of(), List.of())));

        assertThat(ex.getMessage()).isEqualTo(CABECALHO_NAO_ENCONTRADO_MESSAGE);
    }

    @Test
    void ler_ShouldThrow_WhenBytesAreNotXlsx() {
        byte[] texto = "Nome Fantasia;CNPJ\nPosto;12345678000126".getBytes(StandardCharsets.UTF_8);

        PlanilhaInvalidaException ex = assertThrows(PlanilhaInvalidaException.class, () -> ler(texto));

        assertThat(ex.getMessage()).isEqualTo(PLANILHA_INVALIDA_MESSAGE);
        assertThat(ex.getCause()).isNotNull();
    }

    @Test
    void ler_ShouldThrow_WhenFileIsEmpty() {
        PlanilhaInvalidaException ex = assertThrows(PlanilhaInvalidaException.class, () -> ler(new byte[0]));

        assertThat(ex.getMessage()).isEqualTo(PLANILHA_INVALIDA_MESSAGE);
    }

    // ---------- escopo ----------

    @Test
    void ler_ShouldDiscard_WhenOtherUfOrCarWashType() {
        Map<String, Object> outraUf = posto();
        outraUf.put("UF", "PR");
        outraUf.put("CNPJ", "11.111.111/0001-11");
        Map<String, Object> lavador = posto();
        lavador.put("Tipo", "LAVADOR DE CARRO");
        lavador.put("CNPJ", "22.222.222/0001-22");

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(posto(), outraUf, lavador)));

        assertThat(resultado.totalLinhasLidas()).isEqualTo(3);
        assertThat(resultado.totalNoEscopo()).isEqualTo(1);
        assertThat(resultado.linhasValidas()).extracting(LinhaPlanilhaPosto::cnpj).containsExactly("12.345.678/0001-26");
        assertThat(resultado.linhasComErro()).isEmpty();
        assertThat(resultado.avisos()).isEmpty();
    }

    @Test
    void ler_ShouldCompareScope_IgnoringCaseAccentsAndSpaces() {
        LeitorPlanilhaPostos comAcento = new LeitorPlanilhaPostos(List.of(" sc "), List.of("Pósto"));
        Map<String, Object> posto = posto();
        posto.put("UF", "Sc");
        posto.put("Tipo", " posto ");

        ResultadoLeituraPlanilha resultado = comAcento.ler(new ByteArrayInputStream(
                planilha(List.of(), CABECALHO, List.of(posto))));

        assertThat(resultado.totalNoEscopo()).isEqualTo(1);
    }

    @Test
    void ler_ShouldNotCountBlankRows_WhenBetweenDataRows() {
        Map<String, Object> vazia = new LinkedHashMap<>();
        CABECALHO.forEach(nome -> vazia.put(nome, " "));
        Map<String, Object> outro = posto();
        outro.put("CNPJ", "98.765.432/0001-98");

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(posto(), vazia, outro)));

        assertThat(resultado.totalLinhasLidas()).isEqualTo(2);
        assertThat(resultado.linhasValidas()).extracting(LinhaPlanilhaPosto::numeroLinha).containsExactly(2, 4);
    }

    // ---------- duplicidade ----------

    @Test
    void ler_ShouldKeepFirstAndWarn_WhenCnpjIsDuplicated() {
        Map<String, Object> repetido = posto();
        repetido.put("CNPJ", "12345678000126");
        repetido.put("Nome Fantasia", "OUTRO NOME");

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(posto(), repetido)));

        assertThat(resultado.totalNoEscopo()).isEqualTo(2);
        assertThat(resultado.linhasValidas()).hasSize(1);
        assertThat(resultado.linhasValidas().get(0).fantasyName()).isEqualTo("POSTO CENTRAL");
        assertThat(resultado.avisos()).containsExactly(
                new OcorrenciaPlanilha(3, "12.345.678/0001-26", "CNPJ duplicado; mantida a linha 2"));
    }

    @Test
    void ler_ShouldKeepLaterValidRow_WhenFirstOccurrenceHasError() {
        Map<String, Object> comErro = posto();
        comErro.put("Bairro", "");

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(comErro, posto())));

        assertThat(resultado.linhasComErro()).extracting(LinhaPlanilhaPosto::numeroLinha).containsExactly(2);
        assertThat(resultado.linhasValidas()).extracting(LinhaPlanilhaPosto::numeroLinha).containsExactly(3);
        assertThat(resultado.avisos()).isEmpty();
    }

    // ---------- erros de linha ----------

    @Test
    void ler_ShouldReportRowErrorAndKeepCnpj_WhenBairroIsEmpty() {
        Map<String, Object> semBairro = posto();
        semBairro.put("Bairro", "   ");

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(semBairro)));

        assertThat(resultado.linhasValidas()).isEmpty();
        LinhaPlanilhaPosto linha = resultado.linhasComErro().get(0);
        assertThat(linha.erros()).containsExactly("Bairro vazio");
        // A sincronização usa o CNPJ da linha com erro para não desativar o posto.
        assertThat(linha.cnpjDigitos()).isEqualTo("12345678000126");
        assertThat(linha.cnpj()).isEqualTo("12.345.678/0001-26");
    }

    @Test
    void ler_ShouldReportEveryEmptyRequiredColumn_WithSpreadsheetColumnNames() {
        Map<String, Object> vazia = posto();
        vazia.put("Razão Social", null);
        vazia.put("CEP", null);
        vazia.put("Logradouro", null);
        vazia.put("Endereço", null);
        vazia.put("Cidade", null);

        LinhaPlanilhaPosto linha = ler(planilha(List.of(), CABECALHO, List.of(vazia))).linhasComErro().get(0);

        assertThat(linha.erros()).containsExactly(
                "Razão Social vazia", "CEP vazio", "Endereço vazio", "Cidade vazia");
    }

    @Test
    void ler_ShouldReportCepInvalid_WhenNotEightDigits() {
        Map<String, Object> cepCurto = posto();
        cepCurto.put("CEP", "8922-612");

        LinhaPlanilhaPosto linha = ler(planilha(List.of(), CABECALHO, List.of(cepCurto))).linhasComErro().get(0);

        assertThat(linha.erros()).containsExactly("CEP inválido (deve ter 8 dígitos)");
    }

    @Test
    void ler_ShouldReportCnpjInvalid_WhenTextCellHasThirteenDigits() {
        Map<String, Object> curto = posto();
        curto.put("CNPJ", "1234567800012");

        LinhaPlanilhaPosto linha = ler(planilha(List.of(), CABECALHO, List.of(curto))).linhasComErro().get(0);

        // Texto não é completado: 13 dígitos é o que foi digitado.
        assertThat(linha.erros()).containsExactly("CNPJ inválido (deve ter 14 dígitos)");
        assertThat(linha.cnpjDigitos()).isNull();
        assertThat(linha.cnpj()).isNull();
    }

    @Test
    void ler_ShouldPadWithLeadingZero_WhenCnpjCellIsNumericWithThirteenDigits() {
        Map<String, Object> numerico = posto();
        numerico.put("CNPJ", 1234567800012d);

        LinhaPlanilhaPosto linha = ler(planilha(List.of(), CABECALHO, List.of(numerico))).linhasValidas().get(0);

        assertThat(linha.cnpjDigitos()).isEqualTo("01234567800012");
        assertThat(linha.cnpj()).isEqualTo("01.234.567/8000-12");
    }

    @Test
    void ler_ShouldMatchMaskedDuplicate_WhenNumericCnpjIsPadded() {
        Map<String, Object> numerico = posto();
        numerico.put("CNPJ", 1234567800012d);
        Map<String, Object> mascarado = posto();
        mascarado.put("CNPJ", "01.234.567/8000-12");

        ResultadoLeituraPlanilha resultado = ler(planilha(List.of(), CABECALHO, List.of(numerico, mascarado)));

        assertThat(resultado.linhasValidas()).hasSize(1);
        assertThat(resultado.avisos()).extracting(OcorrenciaPlanilha::cnpj).containsExactly("01.234.567/8000-12");
    }

    // ---------- apoio ----------

    private ResultadoLeituraPlanilha ler(byte[] arquivo) {
        return leitor.ler(new ByteArrayInputStream(arquivo));
    }

    private static Map<String, Object> posto() {
        Map<String, Object> posto = new LinkedHashMap<>();
        posto.put("Nome Fantasia", "POSTO CENTRAL");
        posto.put("Razão Social", "POSTO CENTRAL LTDA");
        posto.put("CNPJ", "12.345.678/0001-26");
        posto.put("CEP", "89226120");
        posto.put("Logradouro", "RUA");
        posto.put("Endereço", " RUDOLFO FINDER");
        posto.put("Número", "225");
        posto.put("Bairro", "AVENTUREIRO");
        posto.put("UF", "SC");
        posto.put("Cidade", "JOINVILLE");
        posto.put("Telefone", "47 99623-5372");
        posto.put("Tipo", "POSTO");
        posto.put("Horario Funcionamento", "SEGUNDA A DOMINGO 06:00 AS 22:00");
        posto.put("Bandeira", "BANDEIRA BRANCA");
        return posto;
    }

    /**
     * Preâmbulo nas primeiras linhas, depois o cabeçalho (se houver) e as linhas de dados,
     * cada uma posicionada pela coluna do cabeçalho. Valor {@code Double} vira célula
     * numérica; {@code null} não cria célula.
     */
    private static byte[] planilha(List<List<Object>> preambulo, List<String> cabecalho,
                                   List<Map<String, Object>> linhas) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet aba = workbook.createSheet("Postos");
            int indice = 0;

            for (List<Object> valores : preambulo) {
                Row row = aba.createRow(indice++);
                for (int coluna = 0; coluna < valores.size(); coluna++) {
                    escrever(row, coluna, valores.get(coluna));
                }
            }

            if (!cabecalho.isEmpty()) {
                Row row = aba.createRow(indice++);
                for (int coluna = 0; coluna < cabecalho.size(); coluna++) {
                    escrever(row, coluna, cabecalho.get(coluna));
                }
            }

            for (Map<String, Object> linha : linhas) {
                Row row = aba.createRow(indice++);
                for (int coluna = 0; coluna < cabecalho.size(); coluna++) {
                    escrever(row, coluna, linha.get(cabecalho.get(coluna)));
                }
            }

            workbook.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escrever(Row row, int coluna, Object valor) {
        if (valor instanceof Double numero) {
            row.createCell(coluna).setCellValue(numero);
        } else if (valor != null) {
            row.createCell(coluna).setCellValue(valor.toString());
        }
    }
}
