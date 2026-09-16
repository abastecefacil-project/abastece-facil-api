package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.incident.CreateIncidentRequest;
import com.github.api_abastecefacil.dto.incident.IncidentResponse;
import com.github.api_abastecefacil.model.Incident;
import org.springframework.stereotype.Component;

@Component
public class IncidentMapper {
    public Incident toEntity(CreateIncidentRequest request) {
        return new Incident()
                .setCarPlate(request.licensePlate())
                .setUserName(request.userName())
                .setOccurrenceDate(request.occurrenceDate())
                .setTitle(request.title())
                .setDescription(request.description());

    }

    public IncidentResponse toResponse(Incident entity) {
        return new IncidentResponse(
                entity.getId(),
                entity.getCarPlate(),
                entity.getUserName(),
                entity.getOccurrenceDate(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }
}
