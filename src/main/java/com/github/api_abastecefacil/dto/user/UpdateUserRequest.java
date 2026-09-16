package com.github.api_abastecefacil.dto.user;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        String name,
        @Email(message = "Email inválido")
        String email,
        String password
) {
}
