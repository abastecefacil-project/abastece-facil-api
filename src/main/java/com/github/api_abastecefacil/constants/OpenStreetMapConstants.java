package com.github.api_abastecefacil.constants;

public final class OpenStreetMapConstants {

    private OpenStreetMapConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

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

    /** Consulta simplificada da importação: {@code "<via>, <cidade>, <UF>, Brasil"}. */
    public static final String FALLBACK_FORMAT = "%s, %s, %s, Brasil";
    /** Separa a via do número no {@code address}, como em {@code NormalizadorPlanilhaPostos.endereco}. */
    public static final String SEPARADOR_NUMERO = ",";

    public static final String INTERRUPCAO_ESPERA_MESSAGE =
            "Espera pelo intervalo mínimo entre consultas ao Nominatim foi interrompida";
}
