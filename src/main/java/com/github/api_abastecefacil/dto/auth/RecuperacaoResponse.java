package com.github.api_abastecefacil.dto.auth;

import static com.github.api_abastecefacil.constants.AuthConstants.RECUPERACAO_SOLICITADA_MESSAGE;

/**
 * Resposta de {@code POST /api/auth/recuperacao}.
 *
 * <p><b>Só existe uma instância possível.</b> É por isso que o record tem uma factory sem
 * parâmetros e o serviço nunca constrói outra: e-mail inexistente, conta inativa, conta
 * sem senha definida e conta comum devolvem este mesmo objeto, byte a byte. Um campo a
 * mais — {@code enviado}, {@code encontrado}, um código qualquer — seria exatamente o
 * oráculo de existência de conta que o fluxo inteiro do S4 existe para não dar.
 *
 * <p>Repare que não há nada aqui sobre o resultado do envio, ao contrário do
 * {@code conviteEnviado} do {@code UserResponse}: quando esta resposta é montada, o envio
 * ainda nem começou — ele acontece depois, em outra thread. Ver
 * {@code RecuperacaoSenhaService}.
 */
public record RecuperacaoResponse(String message) {

    public static RecuperacaoResponse padrao() {
        return new RecuperacaoResponse(RECUPERACAO_SOLICITADA_MESSAGE);
    }
}
