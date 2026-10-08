package com.github.api_abastecefacil.constants;

public final class GasStationConstants {

    private GasStationConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final String GAS_STATION_NOT_FOUND_MESSAGE = "Posto não encontrado";
    public static final String CNPJ_ALREADY_EXISTS_MESSAGE = "Já existe um posto cadastrado com esse CNPJ";
    public static final String COORDINATES_NOT_FOUND_MESSAGE =
            "Endereço não localizado no mapa. Confira o endereço ou informe a latitude e a longitude manualmente.";

    /**
     * Casas decimais de {@code gas_stations.latitude} e {@code longitude} ({@code numeric(10,8)}
     * e {@code (11,8)}). A coordenada informada à mão é arredondada aqui, para a resposta
     * devolver o mesmo valor que o banco grava.
     */
    public static final int ESCALA_COORDENADAS = 8;
}