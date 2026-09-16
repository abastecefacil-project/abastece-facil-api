package com.github.api_abastecefacil.dto.auth;

/**
 * Resposta das sondas {@code GET /api/auth/ativacao/validar} e
 * {@code GET /api/auth/recuperacao/validar}.
 *
 * <p>Chamava-se {@code AtivacaoValidacaoResponse} até o S4, quando a recuperação passou a
 * precisar exatamente da mesma sonda com outra finalidade. <b>O JSON não mudou</b>, apenas
 * o nome do tipo.
 *
 * <p>Os dois endpoints respondem <b>200 sempre</b>, inclusive para token inválido: link
 * expirado é desfecho esperado do fluxo, não erro. Quem falhou seria a consulta, não o
 * token.
 *
 * <p><b>A resposta é idêntica para as quatro rejeições</b> — inexistente, expirado, já
 * usado e de finalidade divergente: sempre {@code {"valido": false, "nome": null}}, sem
 * campo dizendo qual caso ocorreu. Mesma razão da mensagem única do M2: distinguir
 * revelaria a quem tem o token se ele existe, se já foi consumido ou para que servia.
 *
 * <p>O {@code nome} existe só para a tela dizer "Olá, Fulano" antes de pedir a senha, e
 * é o único dado do usuário que sai daqui.
 */
public record TokenValidacaoResponse(boolean valido, String nome) {

    public static TokenValidacaoResponse invalido() {
        return new TokenValidacaoResponse(false, null);
    }
}
