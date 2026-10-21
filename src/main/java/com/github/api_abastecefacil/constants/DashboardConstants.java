package com.github.api_abastecefacil.constants;

import java.time.ZoneId;

public final class DashboardConstants {

    private DashboardConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    /**
     * Fuso que define "mês atual" no resumo da Home. Explícito, e não o da JVM: o container
     * não fixa {@code TZ}, e entre 21h e meia-noite do último dia do mês um relógio em UTC
     * já estaria no mês seguinte.
     */
    public static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
}
