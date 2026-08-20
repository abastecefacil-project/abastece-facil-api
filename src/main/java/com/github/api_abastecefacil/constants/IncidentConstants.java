package com.github.api_abastecefacil.constants;

public final class IncidentConstants {

    private IncidentConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final String INCIDENT_NOT_FOUND_MESSAGE = "Nenhum incidente encontrado com esse ID";
    public static final String CAR_NOT_FOUND_BY_PLATE_MESSAGE = "Carro não encontrado com essa placa";

    public static final int LATEST_INCIDENTS_LIMIT = 3;
    public static final String SORT_BY_FIELD = "createdAt";
}