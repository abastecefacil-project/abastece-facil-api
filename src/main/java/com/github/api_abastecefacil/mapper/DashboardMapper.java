package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.dashboard.OcorrenciaRecente;
import com.github.api_abastecefacil.model.Incident;
import org.springframework.stereotype.Component;

@Component
public class DashboardMapper {

    public OcorrenciaRecente toOcorrenciaRecente(Incident entity) {
        return new OcorrenciaRecente(
                entity.getId(),
                entity.getTitle(),
                entity.getCarPlate(),
                entity.getOccurrenceDate()
        );
    }
}
