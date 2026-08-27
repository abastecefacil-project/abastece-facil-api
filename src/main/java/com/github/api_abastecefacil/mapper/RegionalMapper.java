package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.regional.RegionalResponse;
import com.github.api_abastecefacil.model.Regional;
import org.springframework.stereotype.Component;

@Component
public class RegionalMapper {

    public RegionalResponse toResponse(Regional regional) {
        return new RegionalResponse(
                regional.getId(),
                regional.getNome(),
                regional.getSigla(),
                regional.getAtivo(),
                regional.getCreatedAt(),
                regional.getUpdatedAt()
        );
    }
}
