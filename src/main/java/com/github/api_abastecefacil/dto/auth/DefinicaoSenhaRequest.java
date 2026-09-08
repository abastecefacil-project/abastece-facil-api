package com.github.api_abastecefacil.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/auth/ativacao} <b>e</b> de
 * {@code POST /api/auth/recuperacao/confirmar}.
 *
 * <p>Chamava-se {@code AtivacaoRequest} até o S4. O corpo dos dois endpoints é o mesmo par
 * {@code (token, senha)}, com as mesmas validações e a mesma regra sobre onde a senha pode
 * trafegar — um gêmeo só para a recuperação seria duplicação sem nenhuma diferença a
 * justificar. <b>O JSON não mudou</b>, apenas o nome do tipo.
 *
 * <p><b>A senha trafega apenas aqui, no corpo.</b> Nunca em query string: URL vai para
 * log de servidor, histórico de navegador e cabeçalho {@code Referer}, e uma senha em
 * qualquer um desses lugares está vazada.
 *
 * <p>O token dos {@code GET} de validação é a exceção consciente — eles não consomem nada
 * e o token é o que o navegador já entrega ao abrir o link do e-mail.
 *
 * <p>Sem {@code @Size} na senha de propósito: a política inteira mora em
 * {@code UserValidator.validarSenha}, que também precisa do nome e do e-mail do dono da
 * conta. Espalhar metade da regra numa anotação daria duas fontes de verdade e duas
 * mensagens diferentes para o mesmo erro.
 */
public record DefinicaoSenhaRequest(

        @NotBlank(message = "Token é obrigatório")
        String token,

        @NotBlank(message = "Senha é obrigatória")
        String senha
) {
}
