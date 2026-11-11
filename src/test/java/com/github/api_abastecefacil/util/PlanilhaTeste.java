package com.github.api_abastecefacil.util;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

/** Leitura das planilhas exportadas nos testes: abre o .xlsx e devolve uma linha como texto exibido. */
public final class PlanilhaTeste {

    private PlanilhaTeste() {
    }

    public static Workbook abrir(byte[] conteudo) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(conteudo));
    }

    public static List<String> linha(Sheet aba, int indice) {
        DataFormatter formatador = new DataFormatter();
        int colunas = aba.getRow(0).getLastCellNum();
        return IntStream.range(0, colunas)
                .mapToObj(coluna -> formatador.formatCellValue(aba.getRow(indice).getCell(coluna)))
                .toList();
    }
}
