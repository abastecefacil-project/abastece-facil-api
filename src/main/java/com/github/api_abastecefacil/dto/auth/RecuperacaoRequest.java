package com.github.api_abastecefacil.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/auth/recuperacao}: apenas o e-mail.
 *
 * <p>E apenas ele mesmo. Pedir qualquer segundo dado — matrícula, telefone, data de
 * nascimento — como "confirmação" transformaria o endpoint num verificador de cadastro:
 * quem quisesse descobrir a matrícula de alguém tentaria valores até a resposta mudar.
 *
 * <p>O {@code @Email} valida o <b>formato</b> e nada mais. Ele rejeita {@code "abc"} com
 * 400, o que não revela nada sobre conta nenhuma; um endereço bem formado e inexistente
 * segue adiante e recebe a mesma resposta de sucesso que um cadastrado.
 *
 * <p>Não há validação de domínio permitido aqui, ao contrário do cadastro administrativo:
 * responder 400 para um domínio fora da lista diria ao solicitante que nenhuma conta com
 * aquele endereço poderia existir.
 */
public record RecuperacaoRequest(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email
) {
}
