package com.github.api_abastecefacil.dto.auth;

public record AuthResponse(
        String token,
        String type,
        String message
) {
}
