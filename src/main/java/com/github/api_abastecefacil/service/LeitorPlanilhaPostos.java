package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.dto.gasStation.OcorrenciaPlanilha;
import com.github.api_abastecefacil.dto.gasStation.ResultadoLeituraPlanilha;
import com.github.api_abastecefacil.exception.PlanilhaInvalidaException;
import com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos;
import org.apache.poi.EmptyFileException;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.*;
import static com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos.*;

/**
 * Lê a planilha .xlsx de postos credenciados e devolve as linhas normalizadas, filtradas
 * por UF e tipo e deduplicadas por CNPJ. Não toca em banco nem em geocodificação.
 *
 * <p><b>Streaming.</b> O arquivo real tem ~17 mil linhas por 20 colunas; o
 * {@code XSSFWorkbook} montaria a planilha inteira em objetos. A leitura usa a API de
 * eventos do POI ({@code XSSFReader} + {@code XSSFSheetXMLHandler}), que entrega célula a
 * célula. Só a primeira aba é lida.
 *
 * <p><b>Estado de leitura só dentro de {@link #ler}.</b> Este serviço é singleton; o
 * handler, o formatador e tudo o que eles acumulam (colunas, candidata a cabeçalho, marca
 * de célula numérica) nascem e morrem em cada chamada. Guardá-los em campo faria duas
 * leituras simultâneas disputarem o mesmo estado.
 */
@Service
public class LeitorPlanilhaPostos {

    private final Set<String> ufs;
    private final Set<String> tipos;

    public LeitorPlanilhaPostos(
            @Value("${importacao-postos.ufs:SC}") List<String> ufs,
            @Value("${importacao-postos.tipos:POSTO}") List<String> tipos) {
        this.ufs = chaves(ufs);
        this.tipos = chaves(tipos);
    }

    /**
     * Lê a primeira aba da planilha.
     *
     * @throws PlanilhaInvalidaException se o arquivo não é .xlsx legível, se não há
     *                                   cabeçalho reconhecível ou se falta coluna obrigatória
     */
    public ResultadoLeituraPlanilha ler(InputStream arquivo) {
        FormatadorPlanilha formatador = new FormatadorPlanilha();
        Leitura leitura = new Leitura(formatador, ufs, tipos);

        OPCPackage pacote = null;
        try {
            pacote = OPCPackage.open(arquivo);
            XSSFReader reader = new XSSFReader(pacote);
            StylesTable estilos = reader.getStylesTable();
            ReadOnlySharedStringsTable textos = new ReadOnlySharedStringsTable(pacote);

            Iterator<InputStream> abas = reader.getSheetsData();
            if (!abas.hasNext()) {
                throw new PlanilhaInvalidaException(PLANILHA_INVALIDA_MESSAGE);
            }

            // Sem tabela de comentários (null) de propósito: com ela, o handler emite
            // cell() extra para célula vazia comentada, entre o formatador e o cell() da
            // célula corrente — e a marca de célula numérica passaria para a célula errada.
            XMLReader parser = XMLHelper.newXMLReader();
            parser.setContentHandler(new XSSFSheetXMLHandler(estilos, null, textos, leitura, formatador, false));

            try (InputStream primeiraAba = abas.next()) {
                parser.parse(new InputSource(primeiraAba));
            }
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException
                 | UnsupportedFileFormatException | EmptyFileException | POIXMLException e) {
            throw new PlanilhaInvalidaException(PLANILHA_INVALIDA_MESSAGE, e);
        } finally {
            // revert, e não close: aberto de InputStream o pacote é READ_WRITE, e o close
            // tentaria gravar de volta.
            if (pacote != null) {
                pacote.revert();
            }
        }

        return leitura.resultado();
    }

    private static Set<String> chaves(List<String> valores) {
        return valores.stream()
                .map(NormalizadorPlanilhaPostos::chaveComparacao)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * {@code DataFormatter} que marca a célula como numérica e não usa notação científica
     * no formato Geral.
     *
     * <p>O {@code DataFormatter} padrão imita o Excel e escreve número de 11 dígitos ou
     * mais no formato Geral em notação científica: um CNPJ numérico
     * {@code 1234567800012} chegaria como {@code 1.23457E+12}, sem os dígitos.
     *
     * <p><b>Por que a marca é confiável</b> — conferido no código-fonte do POI 5.5.1,
     * {@code XSSFSheetXMLHandler}: {@code endElement} chama {@code outputCell()}, que
     * calcula o texto da célula — chamando {@code formatRawCellContents} só para célula
     * numérica (tipo {@code NUMBER}, ou fórmula com resultado numérico) — e, no fim do
     * mesmo método, chama {@code output.cell(...)} para essa mesma célula. Entre os dois
     * só roda a checagem de comentários, que fica inerte porque o handler é criado sem
     * tabela de comentários. Célula de texto não passa pelo formatador, então a marca
     * lida e zerada em {@code cell()} diz o tipo da célula corrente.
     */
    private static final class FormatadorPlanilha extends DataFormatter {

        private boolean numerica;

        @Override
        public String formatRawCellContents(double valor, int indiceFormato, String formato) {
            numerica = true;
            if (indiceFormato == 0 || FORMATO_GERAL.equalsIgnoreCase(formato)) {
                return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
            }
            return super.formatRawCellContents(valor, indiceFormato, formato);
        }

        boolean consumirMarcaNumerica() {
            boolean era = numerica;
            numerica = false;
            return era;
        }
    }

    /** Todo o estado de uma leitura. Vive só durante uma chamada de {@link #ler}. */
    private static final class Leitura implements SheetContentsHandler {

        private final FormatadorPlanilha formatador;
        private final Set<String> ufs;
        private final Set<String> tipos;

        private final Map<Integer, String> celulas = new HashMap<>();
        private final Set<Integer> colunasNumericas = new HashSet<>();
        private int ultimaColuna = -1;

        /** Nome da coluna obrigatória -> índice. Nulo até o cabeçalho ser encontrado. */
        private Map<String, Integer> colunas;

        private int linhaCandidata;
        private List<String> encontradasCandidata = List.of();

        private int totalLinhasLidas;
        private int totalNoEscopo;
        private final List<LinhaPlanilhaPosto> linhasValidas = new ArrayList<>();
        private final List<LinhaPlanilhaPosto> linhasComErro = new ArrayList<>();
        private final List<OcorrenciaPlanilha> avisos = new ArrayList<>();
        private final Map<String, Integer> primeiraLinhaPorCnpj = new HashMap<>();

        Leitura(FormatadorPlanilha formatador, Set<String> ufs, Set<String> tipos) {
            this.formatador = formatador;
            this.ufs = ufs;
            this.tipos = tipos;
        }

        @Override
        public void startRow(int indiceLinha) {
            celulas.clear();
            colunasNumericas.clear();
            ultimaColuna = -1;
        }

        @Override
        public void cell(String referencia, String valor, XSSFComment comentario) {
            boolean numerica = formatador.consumirMarcaNumerica();
            // Referência ausente é XML válido: a célula ocupa a coluna seguinte.
            int coluna = referencia == null ? ultimaColuna + 1 : new CellReference(referencia).getCol();
            ultimaColuna = coluna;

            if (valor == null) {
                return;
            }
            celulas.put(coluna, valor);
            if (numerica) {
                colunasNumericas.add(coluna);
            }
        }

        @Override
        public void endRow(int indiceLinha) {
            int numeroLinha = indiceLinha + 1;
            if (colunas == null) {
                tentarCabecalho(numeroLinha);
            } else {
                processar(numeroLinha);
            }
        }

        private void tentarCabecalho(int numeroLinha) {
            Map<String, Integer> porChave = new HashMap<>();
            celulas.forEach((coluna, valor) -> {
                String chave = chaveComparacao(valor);
                if (chave != null) {
                    porChave.merge(chave, coluna, Math::min);
                }
            });

            Map<String, Integer> mapeadas = new LinkedHashMap<>();
            for (String nome : COLUNAS_OBRIGATORIAS) {
                Integer coluna = porChave.get(chaveComparacao(nome));
                if (coluna != null) {
                    mapeadas.put(nome, coluna);
                }
            }

            if (mapeadas.size() == COLUNAS_OBRIGATORIAS.size()) {
                colunas = mapeadas;
            } else if (mapeadas.size() > encontradasCandidata.size()) {
                linhaCandidata = numeroLinha;
                encontradasCandidata = List.copyOf(mapeadas.keySet());
            }
        }

        private void processar(int numeroLinha) {
            if (celulas.values().stream().allMatch(valor -> texto(valor) == null)) {
                return;
            }
            totalLinhasLidas++;

            if (!ufs.contains(chaveComparacao(valor(COLUNA_UF)))
                    || !tipos.contains(chaveComparacao(valor(COLUNA_TIPO)))) {
                return;
            }
            totalNoEscopo++;

            LinhaPlanilhaPosto linha = montar(numeroLinha);
            if (!linha.valida()) {
                linhasComErro.add(linha);
                return;
            }

            Integer primeira = primeiraLinhaPorCnpj.putIfAbsent(linha.cnpjDigitos(), numeroLinha);
            if (primeira == null) {
                linhasValidas.add(linha);
            } else {
                avisos.add(new OcorrenciaPlanilha(numeroLinha, linha.cnpj(),
                        String.format(CNPJ_DUPLICADO_MESSAGE, primeira)));
            }
        }

        private LinhaPlanilhaPosto montar(int numeroLinha) {
            String cnpjDigitos = cnpjDigitos(cnpjBruto());
            String name = texto(valor(COLUNA_RAZAO_SOCIAL));
            String cepBruto = valor(COLUNA_CEP);
            String cep = cep(cepBruto);
            String address = endereco(valor(COLUNA_LOGRADOURO), valor(COLUNA_ENDERECO), valor(COLUNA_NUMERO));
            String district = texto(valor(COLUNA_BAIRRO));
            String city = texto(valor(COLUNA_CIDADE));
            String state = texto(valor(COLUNA_UF));

            List<String> erros = new ArrayList<>();
            if (cnpjDigitos == null) {
                erros.add(CNPJ_INVALIDO_MESSAGE);
            }
            if (name == null) {
                erros.add(String.format(CAMPO_VAZIA_MESSAGE, COLUNA_RAZAO_SOCIAL));
            }
            if (cep == null) {
                erros.add(texto(cepBruto) == null
                        ? String.format(CAMPO_VAZIO_MESSAGE, COLUNA_CEP)
                        : CEP_INVALIDO_MESSAGE);
            }
            if (address == null) {
                erros.add(String.format(CAMPO_VAZIO_MESSAGE, COLUNA_ENDERECO));
            }
            if (district == null) {
                erros.add(String.format(CAMPO_VAZIO_MESSAGE, COLUNA_BAIRRO));
            }
            if (city == null) {
                erros.add(String.format(CAMPO_VAZIA_MESSAGE, COLUNA_CIDADE));
            }
            if (state == null) {
                erros.add(String.format(CAMPO_VAZIA_MESSAGE, COLUNA_UF));
            }

            return new LinhaPlanilhaPosto(
                    numeroLinha,
                    cnpjDigitos,
                    cnpjFormatado(cnpjDigitos),
                    name,
                    texto(valor(COLUNA_NOME_FANTASIA)),
                    telefone(valor(COLUNA_TELEFONE)),
                    cep,
                    address,
                    district,
                    city,
                    state,
                    horario(valor(COLUNA_HORARIO)),
                    List.copyOf(erros));
        }

        /**
         * O Excel guarda CNPJ digitado como número e descarta o zero à esquerda. Só a
         * célula numérica é completada até 14 dígitos: em célula de texto, 13 dígitos são
         * o que foi digitado, e o CNPJ é inválido.
         */
        private String cnpjBruto() {
            String bruto = valor(COLUNA_CNPJ);
            if (bruto == null || !colunasNumericas.contains(colunas.get(COLUNA_CNPJ))) {
                return bruto;
            }
            String digitos = NAO_DIGITO.matcher(bruto).replaceAll("");
            if (digitos.isEmpty() || digitos.length() >= CNPJ_TAMANHO) {
                return bruto;
            }
            return "0".repeat(CNPJ_TAMANHO - digitos.length()) + digitos;
        }

        private String valor(String coluna) {
            return celulas.get(colunas.get(coluna));
        }

        ResultadoLeituraPlanilha resultado() {
            if (colunas == null) {
                if (encontradasCandidata.size() >= MINIMO_COLUNAS_CABECALHO_PARCIAL) {
                    List<String> ausentes = COLUNAS_OBRIGATORIAS.stream()
                            .filter(nome -> !encontradasCandidata.contains(nome))
                            .toList();
                    throw new PlanilhaInvalidaException(String.format(
                            COLUNAS_AUSENTES_MESSAGE, linhaCandidata, String.join(", ", ausentes)));
                }
                throw new PlanilhaInvalidaException(CABECALHO_NAO_ENCONTRADO_MESSAGE);
            }

            return new ResultadoLeituraPlanilha(
                    totalLinhasLidas,
                    totalNoEscopo,
                    List.copyOf(linhasValidas),
                    List.copyOf(linhasComErro),
                    List.copyOf(avisos));
        }
    }
}
