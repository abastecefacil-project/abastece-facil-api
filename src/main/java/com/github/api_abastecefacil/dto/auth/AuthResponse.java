package com.github.api_abastecefacil.dto.auth;

import com.github.api_abastecefacil.model.Perfil;

public record AuthResponse(
        String token,
        String type,
        String message,
        Perfil perfil
) {
}
