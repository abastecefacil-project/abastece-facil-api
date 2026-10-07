package com.github.api_abastecefacil.constants;

import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Map.entry;

public final class OpenStreetMapConstants {

    private OpenStreetMapConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    // ------------------------------------------------------------------ parâmetros do /search

    /** Busca em texto livre. O Nominatim recusa {@code q} combinado com os campos estruturados. */
    public static final String PARAM_QUERY = "q";
    public static final String PARAM_STREET = "street";
    public static final String PARAM_CITY = "city";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_COUNTRY = "country";
    public static final String PARAM_FORMAT = "format";
    public static final String PARAM_ADDRESS_DETAILS = "addressdetails";
    public static final String PARAM_LIMIT = "limit";
    public static final String PARAM_COUNTRY_CODES = "countrycodes";
    public static final String HEADER_USER_AGENT = "User-Agent";

    // ------------------------------------------------------------------ valores

    public static final String FORMAT_JSON = "json";
    public static final int DEFAULT_LIMIT = 1;
    public static final int DEFAULT_ADDRESS_DETAIL = 1;

    /** Restringe a busca ao Brasil (parâmetro {@code countrycodes} do Nominatim). */
    public static final String COUNTRY_CODES_BRASIL = "br";

    public static final String LATITUDE_KEY = "lat";
    public static final String LONGITUDE_KEY = "lon";

    /** Objeto aninhado que o Nominatim devolve quando {@code addressdetails=1}. */
    public static final String ADDRESS_KEY = "address";
    /** Código ISO da subdivisão dentro de {@code address}, no formato {@code "BR-SC"}. */
    public static final String ISO_SUBDIVISAO_KEY = "ISO3166-2-lvl4";
    public static final String PREFIXO_ISO_BRASIL = "BR-";

    /** Valor de {@code country} na busca estruturada. */
    public static final String PAIS_BRASIL = "Brasil";

    /** Consulta de fallback, em texto livre e sem CEP: {@code "<via>, <cidade>, <UF>, Brasil"}. */
    public static final String FALLBACK_FORMAT = "%s, %s, %s, Brasil";
    /** {@code street} da busca estruturada com número: {@code "<número> <via>"}. */
    public static final String STREET_COM_NUMERO_FORMAT = "%s %s";
    /** Separa a via do número no {@code address}, como em {@code NormalizadorPlanilhaPostos.endereco}. */
    public static final String SEPARADOR_NUMERO = ",";

    /**
     * Número que vai para o {@code street}: dígitos, com ou sem uma letra ({@code "622"},
     * {@code "1285 E"}, {@code "1285E"}). Todo o resto — {@code "S/N"}, vazio,
     * {@code "KM 206"} — fica de fora, e a busca segue só com a via.
     */
    public static final Pattern NUMERO_PREDIAL = Pattern.compile("^\\d+\\s*[A-Za-z]?$");

    /**
     * Nome de cada UF, para o parâmetro {@code state} da busca estruturada: o Nominatim
     * casa o nome do estado, não a sigla. As 27 unidades da federação.
     */
    public static final Map<String, String> NOME_POR_UF = Map.ofEntries(
            entry("AC", "Acre"),
            entry("AL", "Alagoas"),
            entry("AP", "Amapá"),
            entry("AM", "Amazonas"),
            entry("BA", "Bahia"),
            entry("CE", "Ceará"),
            entry("DF", "Distrito Federal"),
            entry("ES", "Espírito Santo"),
            entry("GO", "Goiás"),
            entry("MA", "Maranhão"),
            entry("MT", "Mato Grosso"),
            entry("MS", "Mato Grosso do Sul"),
            entry("MG", "Minas Gerais"),
            entry("PA", "Pará"),
            entry("PB", "Paraíba"),
            entry("PR", "Paraná"),
            entry("PE", "Pernambuco"),
            entry("PI", "Piauí"),
            entry("RJ", "Rio de Janeiro"),
            entry("RN", "Rio Grande do Norte"),
            entry("RS", "Rio Grande do Sul"),
            entry("RO", "Rondônia"),
            entry("RR", "Roraima"),
            entry("SC", "Santa Catarina"),
            entry("SP", "São Paulo"),
            entry("SE", "Sergipe"),
            entry("TO", "Tocantins")
    );

    public static final String INTERRUPCAO_ESPERA_MESSAGE =
            "Espera pelo intervalo mínimo entre consultas ao Nominatim foi interrompida";
}
