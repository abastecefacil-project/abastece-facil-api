package com.github.api_abastecefacil.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo do {@code POST /api/auth/ativacao}.
 *
 * <p><b>A senha trafega apenas aqui, no corpo.</b> Nunca em query string: URL vai para
 * log de servidor, histórico de navegador e cabeçalho {@code Referer}, e uma senha em
 * qualquer um desses lugares está vazada.
 *
 * <p>O token do {@code GET} de validação é a exceção consciente — ele não consome nada e
 * é o que o navegador já entrega ao abrir o link do e-mail.
 *
 * <p>Sem {@code @Size} na senha de propósito: a política inteira mora em
 * {@code UserValidator.validarSenha}, que também precisa do nome e do e-mail do dono da
 * conta. Espalhar metade da regra numa anotação daria duas fontes de verdade e duas
 * mensagens diferentes para o mesmo erro.
 */
public record AtivacaoRequest(

        @NotBlank(message = "Token é obrigatório")
        String token,

        @NotBlank(message = "Senha é obrigatória")
        String senha
) {
}
