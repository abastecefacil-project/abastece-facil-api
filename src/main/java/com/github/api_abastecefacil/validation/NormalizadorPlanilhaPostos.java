package com.github.api_abastecefacil.validation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.*;

/**
 * Normalização das células da planilha de postos credenciados.
 *
 * <p>Funções puras: não leem arquivo, não consultam banco e não lançam exceção por dado
 * ruim. Valor que não pode ser normalizado com segurança vira {@code null}, e quem decide
 * o que fazer com a linha é o importador.
 */
public final class NormalizadorPlanilhaPostos {

    private NormalizadorPlanilhaPostos() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    /** Só os dígitos, quando são exatamente 14; qualquer outra coisa vira {@code null}. */
    public static String cnpjDigitos(String valor) {
        String digitos = digitos(valor);
        return digitos != null && digitos.length() == CNPJ_TAMANHO ? digitos : null;
    }

    /** {@code XX.XXX.XXX/XXXX-XX}, a partir de exatamente 14 dígitos. */
    public static String cnpjFormatado(String digitos) {
        if (digitos == null || !CNPJ_DIGITOS.matcher(digitos).matches()) {
            return null;
        }
        return String.format(CNPJ_FORMATO,
                digitos.substring(0, 2), digitos.substring(2, 5), digitos.substring(5, 8),
                digitos.substring(8, 12), digitos.substring(12));
    }

    /**
     * {@code XXXXX-XXX}, o formato que o formulário de postos envia. O seed grava só
     * dígitos, mas é o formulário o caminho de escrita em uso.
     */
    public static String cep(String valor) {
        String digitos = digitos(valor);
        if (digitos == null || digitos.length() != CEP_TAMANHO) {
            return null;
        }
        return String.format(CEP_FORMATO, digitos.substring(0, 5), digitos.substring(5));
    }

    /** Trim e colapso de qualquer sequência de espaço em branco num espaço; vazio vira {@code null}. */
    public static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = ESPACOS.matcher(valor).replaceAll(" ").trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    /**
     * Chave para comparar textos: {@link #texto} sem acento e em caixa baixa. Não é valor
     * de exibição nem de gravação. Usada no cabeçalho da planilha, no filtro de UF e tipo
     * e para decidir se uma mudança de endereço exige geocodificar.
     */
    public static String chaveComparacao(String valor) {
        String normalizado = texto(valor);
        if (normalizado == null) {
            return null;
        }
        String semAcento = DIACRITICOS.matcher(Normalizer.normalize(normalizado, Normalizer.Form.NFD)).replaceAll("");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    /**
     * Ausência de número, em qualquer grafia, vira {@code "S/N"}. O resto fica como veio
     * ({@code "1285 E"}, {@code "KM 206"}), só sem vírgula — ver {@link #endereco}.
     */
    public static String numero(String valor) {
        String normalizado = texto(semVirgula(valor));
        if (normalizado == null || SEM_NUMERO.matcher(normalizado).matches()) {
            return SEM_NUMERO_PADRAO;
        }
        return normalizado;
    }

    /**
     * {@code "<LOGRADOURO> <ENDEREÇO>, <NÚMERO>"}, sem alterar caixa.
     *
     * <p>A vírgula é retirada de todas as partes porque ela é o separador: a tela de
     * postos divide o endereço em rua e número por {@code split(',')}, então uma vírgula
     * a mais jogaria parte da rua no campo do número.
     */
    public static String endereco(String logradouro, String endereco, String numero) {
        String via = Stream.of(logradouro, endereco)
                .map(parte -> texto(semVirgula(parte)))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        if (via.isEmpty()) {
            return null;
        }
        return via + ", " + numero(numero);
    }

    /**
     * Com 10 ou 11 dígitos, a máscara do formulário ({@code formatarTelefone}, em
     * {@code mascaras.js}): {@code (47) 3422-1234} e {@code (47) 99999-8888}. Fora disso o
     * texto é mantido, porque não há máscara que caiba.
     */
    public static String telefone(String valor) {
        String normalizado = texto(valor);
        if (normalizado == null) {
            return null;
        }

        String digitos = NAO_DIGITO.matcher(normalizado).replaceAll("");
        if (digitos.length() == TELEFONE_FIXO_TAMANHO) {
            return String.format(TELEFONE_FORMATO, digitos.substring(0, 2), digitos.substring(2, 6), digitos.substring(6));
        }
        if (digitos.length() == TELEFONE_CELULAR_TAMANHO) {
            return String.format(TELEFONE_FORMATO, digitos.substring(0, 2), digitos.substring(2, 7), digitos.substring(7));
        }
        return normalizado;
    }

    /**
     * Horário de funcionamento no formato {@code "HH:mm - HH:mm"}, ou {@code null}.
     *
     * <p>Regras, nesta ordem:
     * <ol>
     *   <li>exatamente dois horários explícitos, diferentes entre si, e nenhum indicador
     *       livre de 24 horas: o intervalo;</li>
     *   <li>todos os horários explícitos são 24h (vale também sem nenhum) e há indicador
     *       livre ou ao menos um horário: {@code "00:00 - 23:59"};</li>
     *   <li>qualquer outro caso: {@code null}.</li>
     * </ol>
     *
     * <p>A regra é conservadora de propósito. Textos mistos, como
     * {@code "24 HORAS de segunda a sábado DOMINGO DAS 06:00 AS 22:00"}, descrevem mais de
     * um horário e não cabem numa string só — e escolher um deles publicaria no mapa um
     * horário errado para parte da semana. Dado ambíguo vira {@code null}. Dias da semana
     * não são interpretados.
     *
     * <p>Horário fora da faixa (hora acima de 24, minuto acima de 59, ou 24 com minuto
     * diferente de 00) também resulta em {@code null}: a célula não é confiável.
     */
    public static String horario(String valor) {
        String normalizado = texto(valor);
        if (normalizado == null) {
            return null;
        }

        List<Horario> horarios = new ArrayList<>();
        StringBuilder restante = new StringBuilder(normalizado);
        Matcher matcher = HORARIO_TOKEN.matcher(normalizado);

        while (matcher.find()) {
            Horario horario = Horario.de(matcher);
            if (!horario.valido()) {
                return null;
            }
            horarios.add(horario);
            for (int i = matcher.start(); i < matcher.end(); i++) {
                restante.setCharAt(i, ' ');
            }
        }

        boolean indicadorLivre = INDICADOR_24H.matcher(restante).find();

        if (horarios.size() == 2 && !horarios.get(0).equals(horarios.get(1)) && !indicadorLivre) {
            return String.format(INTERVALO_FORMATO, horarios.get(0).formatado(), horarios.get(1).formatado());
        }

        boolean todosVinteQuatro = horarios.stream().allMatch(Horario::vinteQuatro);
        if (todosVinteQuatro && (indicadorLivre || !horarios.isEmpty())) {
            return INTERVALO_24H;
        }

        return null;
    }

    private static String digitos(String valor) {
        return valor == null ? null : NAO_DIGITO.matcher(valor).replaceAll("");
    }

    private static String semVirgula(String valor) {
        return valor == null ? null : valor.replace(',', ' ');
    }

    private record Horario(int hora, int minuto) {

        /** Lê o grupo da alternativa que casou em {@code HORARIO_TOKEN}. */
        static Horario de(Matcher matcher) {
            if (matcher.group(1) != null) {
                return new Horario(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            }
            if (matcher.group(3) != null) {
                return new Horario(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
            }
            return new Horario(Integer.parseInt(matcher.group(5)), 0);
        }

        boolean valido() {
            if (hora == HORA_24) {
                return minuto == 0;
            }
            return hora < HORA_24 && minuto < 60;
        }

        boolean vinteQuatro() {
            return hora == HORA_24;
        }

        String formatado() {
            return vinteQuatro() ? FIM_DO_DIA : String.format(HORA_FORMATO, hora, minuto);
        }
    }
}
