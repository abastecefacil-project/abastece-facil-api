package com.github.api_abastecefacil.constants;

public final class OpenStreetMapConstants {

    private OpenStreetMapConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final String FORMAT_JSON = "json";
    public static final int DEFAULT_LIMIT = 1;
    public static final int DEFAULT_ADDRESS_DETAIL = 1;

    public static final String LATITUDE_KEY = "lat";
    public static final String LONGITUDE_KEY = "lon";
}