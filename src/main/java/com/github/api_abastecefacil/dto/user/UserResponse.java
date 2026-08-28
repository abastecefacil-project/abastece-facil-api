package com.github.api_abastecefacil.dto.user;

import com.github.api_abastecefacil.dto.regional.RegionalSummaryResponse;
import com.github.api_abastecefacil.model.Perfil;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Perfil perfil,
        RegionalSummaryResponse regional
) {
}
