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

    /** Tamanho da série de ocorrências por mês, com o mês atual incluído. */
    public static final int MESES_SERIE_OCORRENCIAS = 6;

    /** Janela do ranking de placas: os últimos N dias de calendário, hoje incluído. */
    public static final int DIAS_RANKING_VEICULOS = 90;
    public static final int LIMITE_RANKING_VEICULOS = 5;

    public static final int LIMITE_RANKING_CIDADES = 5;

    /** Convite pendente há mais que isto conta como "antigo" no resumo. */
    public static final int DIAS_CONVITE_PENDENTE_ANTIGO = 7;

    public static final int LIMITE_ULTIMAS_OCORRENCIAS = 5;
}
