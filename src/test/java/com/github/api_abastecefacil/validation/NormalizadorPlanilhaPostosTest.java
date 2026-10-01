package com.github.api_abastecefacil.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorPlanilhaPostosTest {

    // ---------- cnpjDigitos ----------

    @Test
    void cnpjDigitos_ShouldReturnDigits_WhenMasked() {
        assertThat(NormalizadorPlanilhaPostos.cnpjDigitos("12.345.678/0001-26")).isEqualTo("12345678000126");
    }

    @Test
    void cnpjDigitos_ShouldReturnDigits_WhenUnmasked() {
        assertThat(NormalizadorPlanilhaPostos.cnpjDigitos(" 12345678000126 ")).isEqualTo("12345678000126");
    }

    @Test
    void cnpjDigitos_ShouldReturnNull_WhenThirteenDigits() {
        assertThat(NormalizadorPlanilhaPostos.cnpjDigitos("1234567800012")).isNull();
    }

    @Test
    void cnpjDigitos_ShouldReturnNull_WhenFifteenDigits() {
        assertThat(NormalizadorPlanilhaPostos.cnpjDigitos("123456780001266")).isNull();
    }

    @Test
    void cnpjDigitos_ShouldReturnNull_WhenNullOrBlank() {
        assertThat(NormalizadorPlanilhaPostos.cnpjDigitos(null)).isNull();
        assertThat(NormalizadorPlanilhaPostos.cnpjDigitos("  ")).isNull();
    }

    // ---------- cnpjFormatado ----------

    @Test
    void cnpjFormatado_ShouldApplyFrontendMask_WhenFourteenDigits() {
        assertThat(NormalizadorPlanilhaPostos.cnpjFormatado("12345678000126")).isEqualTo("12.345.678/0001-26");
    }

    @Test
    void cnpjFormatado_ShouldReturnNull_WhenNotExactlyFourteenDigits() {
        assertThat(NormalizadorPlanilhaPostos.cnpjFormatado("1234567800012")).isNull();
        assertThat(NormalizadorPlanilhaPostos.cnpjFormatado("12.345.678/0001-26")).isNull();
        assertThat(NormalizadorPlanilhaPostos.cnpjFormatado(null)).isNull();
    }

    // ---------- cep ----------

    @Test
    void cep_ShouldFormatWithHyphen_WhenOnlyDigits() {
        assertThat(NormalizadorPlanilhaPostos.cep("89226120")).isEqualTo("89226-120");
    }

    @Test
    void cep_ShouldKeepHyphen_WhenAlreadyMasked() {
        assertThat(NormalizadorPlanilhaPostos.cep(" 89226-120 ")).isEqualTo("89226-120");
    }

    @Test
    void cep_ShouldReturnNull_WhenNotEightDigits() {
        assertThat(NormalizadorPlanilhaPostos.cep("8922612")).isNull();
        assertThat(NormalizadorPlanilhaPostos.cep("892261200")).isNull();
    }

    @Test
    void cep_ShouldReturnNull_WhenNullOrBlank() {
        assertThat(NormalizadorPlanilhaPostos.cep(null)).isNull();
        assertThat(NormalizadorPlanilhaPostos.cep("")).isNull();
    }

    // ---------- texto ----------

    @Test
    void texto_ShouldTrimAndCollapse_WhenTabsAndLineBreaks() {
        assertThat(NormalizadorPlanilhaPostos.texto("  Posto\t\tdo \n Pedrinho \n")).isEqualTo("Posto do Pedrinho");
    }

    @Test
    void texto_ShouldTreatNonBreakingSpaceAsSpace_WhenPresent() {
        assertThat(NormalizadorPlanilhaPostos.texto(" Posto  Central ")).isEqualTo("Posto Central");
    }

    @Test
    void texto_ShouldKeepCase_WhenNormalizing() {
        assertThat(NormalizadorPlanilhaPostos.texto("Posto DO pedrinho")).isEqualTo("Posto DO pedrinho");
    }

    @Test
    void texto_ShouldReturnNull_WhenOnlyWhitespace() {
        assertThat(NormalizadorPlanilhaPostos.texto(" \t\n ")).isNull();
        assertThat(NormalizadorPlanilhaPostos.texto("")).isNull();
    }

    @Test
    void texto_ShouldReturnNull_WhenNull() {
        assertThat(NormalizadorPlanilhaPostos.texto(null)).isNull();
    }

    // ---------- numero ----------

    @Test
    void numero_ShouldReturnSemNumero_WhenSpaceSeparated() {
        assertThat(NormalizadorPlanilhaPostos.numero("S N")).isEqualTo("S/N");
    }

    @Test
    void numero_ShouldReturnSemNumero_WhenJoined() {
        assertThat(NormalizadorPlanilhaPostos.numero("SN")).isEqualTo("S/N");
    }

    @Test
    void numero_ShouldReturnSemNumero_WhenSlashOrDot() {
        assertThat(NormalizadorPlanilhaPostos.numero("S/N")).isEqualTo("S/N");
        assertThat(NormalizadorPlanilhaPostos.numero("S.N")).isEqualTo("S/N");
    }

    @Test
    void numero_ShouldReturnSemNumero_WhenLowercase() {
        assertThat(NormalizadorPlanilhaPostos.numero("s/n")).isEqualTo("S/N");
    }

    @Test
    void numero_ShouldReturnSemNumero_WhenNullOrBlank() {
        assertThat(NormalizadorPlanilhaPostos.numero(null)).isEqualTo("S/N");
        assertThat(NormalizadorPlanilhaPostos.numero("  ")).isEqualTo("S/N");
    }

    @Test
    void numero_ShouldKeepValue_WhenNumberWithLetter() {
        assertThat(NormalizadorPlanilhaPostos.numero(" 1285 E ")).isEqualTo("1285 E");
    }

    @Test
    void numero_ShouldKeepValue_WhenKilometer() {
        assertThat(NormalizadorPlanilhaPostos.numero("KM 206")).isEqualTo("KM 206");
    }

    @Test
    void numero_ShouldRemoveComma_WhenPresent() {
        assertThat(NormalizadorPlanilhaPostos.numero("1285, FUNDOS")).isEqualTo("1285 FUNDOS");
    }

    // ---------- endereco ----------

    @Test
    void endereco_ShouldJoinParts_WhenPartHasLeadingSpace() {
        assertThat(NormalizadorPlanilhaPostos.endereco("AVENIDA", " GENERAL BENTO GONCALVES", "1420"))
                .isEqualTo("AVENIDA GENERAL BENTO GONCALVES, 1420");
    }

    @Test
    void endereco_ShouldRemoveCommas_WhenViaContainsComma() {
        assertThat(NormalizadorPlanilhaPostos.endereco("RODOVIA", "BR-101, KM 40", "S N"))
                .isEqualTo("RODOVIA BR-101 KM 40, S/N");
    }

    @Test
    void endereco_ShouldUseSemNumero_WhenNumberIsNull() {
        assertThat(NormalizadorPlanilhaPostos.endereco("RUA", "DOM PEDRO II", null))
                .isEqualTo("RUA DOM PEDRO II, S/N");
    }

    @Test
    void endereco_ShouldUseOnlyEndereco_WhenLogradouroIsBlank() {
        assertThat(NormalizadorPlanilhaPostos.endereco(" ", "Rua Rudolfo Finder", "225"))
                .isEqualTo("Rua Rudolfo Finder, 225");
    }

    @Test
    void endereco_ShouldReturnNull_WhenViaIsEmpty() {
        assertThat(NormalizadorPlanilhaPostos.endereco(null, " , ", "100")).isNull();
    }

    // ---------- telefone ----------

    @Test
    void telefone_ShouldApplyLandlineMask_WhenTenDigits() {
        assertThat(NormalizadorPlanilhaPostos.telefone("4734221234")).isEqualTo("(47) 3422-1234");
    }

    @Test
    void telefone_ShouldApplyMobileMask_WhenElevenDigits() {
        assertThat(NormalizadorPlanilhaPostos.telefone("47 9 9623-5372")).isEqualTo("(47) 99623-5372");
    }

    @Test
    void telefone_ShouldKeepMask_WhenAlreadyMasked() {
        assertThat(NormalizadorPlanilhaPostos.telefone("(47) 99999-8888")).isEqualTo("(47) 99999-8888");
    }

    @Test
    void telefone_ShouldReturnText_WhenDigitCountDoesNotFit() {
        assertThat(NormalizadorPlanilhaPostos.telefone(" +55  47 99999-8888 ")).isEqualTo("+55 47 99999-8888");
    }

    @Test
    void telefone_ShouldReturnNull_WhenNullOrBlank() {
        assertThat(NormalizadorPlanilhaPostos.telefone(null)).isNull();
        assertThat(NormalizadorPlanilhaPostos.telefone(" ")).isNull();
    }

    // ---------- horario: indicador de 24 horas ----------

    @Test
    void horario_ShouldReturnFullDay_When24Horas() {
        assertThat(NormalizadorPlanilhaPostos.horario("24 Horas")).isEqualTo("00:00 - 23:59");
    }

    @Test
    void horario_ShouldReturnFullDay_When24hr() {
        assertThat(NormalizadorPlanilhaPostos.horario("24hr")).isEqualTo("00:00 - 23:59");
    }

    @Test
    void horario_ShouldReturnFullDay_WhenEstabelecimento24Horas() {
        assertThat(NormalizadorPlanilhaPostos.horario("Estabelecimento 24 horas")).isEqualTo("00:00 - 23:59");
    }

    @Test
    void horario_ShouldReturnFullDay_When24HRSGlued() {
        assertThat(NormalizadorPlanilhaPostos.horario("HORARIO DE FUNCIONAMENTO 24HRS")).isEqualTo("00:00 - 23:59");
    }

    @Test
    void horario_ShouldReturnFullDay_WhenIndicatorRepeated() {
        assertThat(NormalizadorPlanilhaPostos.horario("24 HRS Conveniência 24 HRS Abastecimento"))
                .isEqualTo("00:00 - 23:59");
    }

    // ---------- horario: intervalo ----------

    @Test
    void horario_ShouldReturnRange_WhenUppercaseWithAs() {
        assertThat(NormalizadorPlanilhaPostos.horario("SEGUNDA A DOMINGO 06:00 AS 22:00")).isEqualTo("06:00 - 22:00");
    }

    @Test
    void horario_ShouldReturnRange_WhenTabsAndLineBreaks() {
        assertThat(NormalizadorPlanilhaPostos.horario("Segunda á domingo das 05:00 às\t23:00\t\t\n"))
                .isEqualTo("05:00 - 23:00");
    }

    @Test
    void horario_ShouldReturnRange_WhenHyphenSeparated() {
        assertThat(NormalizadorPlanilhaPostos.horario("Segunda á Domingo 06:00 - 22:00")).isEqualTo("06:00 - 22:00");
    }

    @Test
    void horario_ShouldCapAt2359_WhenClosingIs2400() {
        assertThat(NormalizadorPlanilhaPostos.horario("DOM A DOM: 06:00 AS 24:00")).isEqualTo("06:00 - 23:59");
    }

    @Test
    void horario_ShouldPadHours_WhenHourSuffix() {
        assertThat(NormalizadorPlanilhaPostos.horario("Horário de funcionamento: 07h as 22h segunda a segunda"))
                .isEqualTo("07:00 - 22:00");
    }

    @Test
    void horario_ShouldCapAt2359_WhenHrSuffixUntil24() {
        assertThat(NormalizadorPlanilhaPostos.horario("Segunda a Domingo das 6hr as 24hr")).isEqualTo("06:00 - 23:59");
    }

    @Test
    void horario_ShouldCapAt2359_WhenHSuffixUntil24() {
        assertThat(NormalizadorPlanilhaPostos.horario("06h às 24h")).isEqualTo("06:00 - 23:59");
    }

    @Test
    void horario_ShouldReadMinutes_WhenHourMinuteWithH() {
        assertThat(NormalizadorPlanilhaPostos.horario("das 7h30 às 22h")).isEqualTo("07:30 - 22:00");
    }

    @Test
    void horario_ShouldPadHours_WhenSingleDigitWithColon() {
        assertThat(NormalizadorPlanilhaPostos.horario("6:30hs as 21:30hs")).isEqualTo("06:30 - 21:30");
    }

    // ---------- horario: ambíguo ou inválido ----------

    @Test
    void horario_ShouldReturnNull_WhenMultipleRanges() {
        assertThat(NormalizadorPlanilhaPostos.horario(
                "Segunda a sexta 6:30hs as 21:30hs\nSábado 7h as 20h\nDomingo 8h as 19h")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenWeekdayOrdinalsAndTwoRanges() {
        assertThat(NormalizadorPlanilhaPostos.horario("2ª a 6ª das 05:00 às 21:00 sábado das 05:00 às 21:00")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_When24HorasBeforeSundayRange() {
        assertThat(NormalizadorPlanilhaPostos.horario("24 HORAS de segunda a sábado DOMINGO DAS 06:00 AS 22:00"))
                .isNull();
    }

    @Test
    void horario_ShouldReturnNull_When24HorasWithHyphenBeforeSundayRange() {
        assertThat(NormalizadorPlanilhaPostos.horario("De Segunda à Sábado - 24 horas Domingo das 06:00 AS 22:00"))
                .isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenTwoRangesWithHsSuffix() {
        assertThat(NormalizadorPlanilhaPostos.horario(
                "SEGUNDA Á SABADO DAS 05:30 ÁS 24HS DOMINGO E FERIADOS DAS 06:00 ÁS 22HS")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_When24HorasAndSaturdayRange() {
        assertThat(NormalizadorPlanilhaPostos.horario("DOMINGO A SEXTA 24 HORAS SÁBADO 00:00 AS 06:00")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_When24HAndRangeInSameText() {
        assertThat(NormalizadorPlanilhaPostos.horario(
                "24 H Para atendimento com pagamento em cartões horário de funcionamento é das 06:00 as 22:00"))
                .isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenSingleTime() {
        assertThat(NormalizadorPlanilhaPostos.horario("Abre às 06:00")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenTwoEqualTimes() {
        assertThat(NormalizadorPlanilhaPostos.horario("06:00 às 06:00")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenTimeOutOfRange() {
        assertThat(NormalizadorPlanilhaPostos.horario("06:00 às 25:00")).isNull();
        assertThat(NormalizadorPlanilhaPostos.horario("06:00 às 24:30")).isNull();
        assertThat(NormalizadorPlanilhaPostos.horario("06:60 às 22:00")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenNoTime() {
        assertThat(NormalizadorPlanilhaPostos.horario("Segunda a sábado")).isNull();
    }

    @Test
    void horario_ShouldReturnNull_WhenNullOrEmpty() {
        assertThat(NormalizadorPlanilhaPostos.horario(null)).isNull();
        assertThat(NormalizadorPlanilhaPostos.horario("")).isNull();
    }
}
