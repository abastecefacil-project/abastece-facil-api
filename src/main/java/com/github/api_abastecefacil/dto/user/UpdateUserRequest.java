package com.github.api_abastecefacil.dto.user;

import jakarta.validation.constraints.Email;
import com.github.api_abastecefacil.model.Perfil;

public record UpdateUserRequest(
        String name,
        @Email(message = "Email inválido")
        String email,
        String password,
        Long regionalId,
        String telefone,
        String matricula,
        Perfil perfil
) {
}
