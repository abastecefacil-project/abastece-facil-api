package com.github.api_abastecefacil.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exportação de listagens para .xlsx: uma aba, cabeçalho em negrito e uma linha por registro.
 * Só relatório — não é compatível com a planilha de importação de postos.
 */
public final class PlanilhaXlsx {

    public static final MediaType TIPO_XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final String FORMATO_DATA = "dd/MM/yyyy";
    private static final DateTimeFormatter FORMATADOR_DATA = DateTimeFormatter.ofPattern(FORMATO_DATA);
    // O container não fixa TZ; o nome do arquivo segue o calendário de quem usa, como o dashboard.
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int LARGURA_MINIMA = 10;
    private static final int LARGURA_MAXIMA = 60;

    private PlanilhaXlsx() {
    }

    /**
     * Valores aceitos: {@code Boolean} vira "Ativo"/"Inativo", {@code LocalDate} e
     * {@code LocalDateTime} viram data {@code dd/MM/yyyy}, {@code null} vira célula vazia e o
     * resto vira texto.
     */
    public static byte[] gerar(List<String> cabecalhos, List<List<Object>> linhas) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet aba = workbook.createSheet("Dados");
            int[] larguras = new int[cabecalhos.size()];

            Font negrito = workbook.createFont();
            negrito.setBold(true);
            CellStyle estiloCabecalho = workbook.createCellStyle();
            estiloCabecalho.setFont(negrito);
            CellStyle estiloData = workbook.createCellStyle();
            estiloData.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(FORMATO_DATA));

            Row cabecalho = aba.createRow(0);
            for (int coluna = 0; coluna < cabecalhos.size(); coluna++) {
                Cell celula = cabecalho.createCell(coluna);
                celula.setCellValue(cabecalhos.get(coluna));
                celula.setCellStyle(estiloCabecalho);
                larguras[coluna] = cabecalhos.get(coluna).length();
            }

            for (int i = 0; i < linhas.size(); i++) {
                Row linha = aba.createRow(i + 1);
                List<Object> valores = linhas.get(i);
                for (int coluna = 0; coluna < valores.size(); coluna++) {
                    String texto = preencher(linha.createCell(coluna), valores.get(coluna), estiloData);
                    larguras[coluna] = Math.max(larguras[coluna], texto.length());
                }
            }

            // Sem autoSizeColumn: ele depende de fontes AWT, que faltam em container headless.
            for (int coluna = 0; coluna < larguras.length; coluna++) {
                int caracteres = Math.min(Math.max(larguras[coluna], LARGURA_MINIMA), LARGURA_MAXIMA) + 2;
                aba.setColumnWidth(coluna, caracteres * 256);
            }

            workbook.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar a planilha", e);
        }
    }

    /** Resposta de download: {@code attachment; filename="<prefixo>-AAAA-MM-DD.xlsx"}. */
    public static ResponseEntity<byte[]> resposta(String prefixo, byte[] conteudo) {
        String nomeArquivo = prefixo + "-" + LocalDate.now(FUSO) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nomeArquivo).build().toString())
                .body(conteudo);
    }

    /** Preenche a célula e devolve o texto exibido, para a largura da coluna. */
    private static String preencher(Cell celula, Object valor, CellStyle estiloData) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Boolean ativo) {
            String texto = ativo ? "Ativo" : "Inativo";
            celula.setCellValue(texto);
            return texto;
        }
        if (valor instanceof LocalDateTime dataHora) {
            valor = dataHora.toLocalDate();
        }
        if (valor instanceof LocalDate data) {
            celula.setCellValue(data);
            celula.setCellStyle(estiloData);
            return data.format(FORMATADOR_DATA);
        }
        String texto = valor.toString();
        celula.setCellValue(texto);
        return texto;
    }
}
