package com.github.api_abastecefacil.constants;

public final class GasStationConstants {

    private GasStationConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final String GAS_STATION_NOT_FOUND_MESSAGE = "Posto não encontrado";
    public static final String CNPJ_ALREADY_EXISTS_MESSAGE = "Já existe um posto cadastrado com esse CNPJ";
    public static final String COORDINATES_NOT_FOUND_MESSAGE = "Não foi possível obter coordenadas para o endereço informado";

    public static final String ADDRESS_FORMAT = "%s, %s, %s, %s, %s";
}