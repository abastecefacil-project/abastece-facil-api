package com.github.api_abastecefacil.constants;

/**
 * Mensagens dos 403 do cadastro operacional — postos, veículos e ocorrências.
 *
 * <p>Separadas das do {@link UserConstants} porque descrevem outro recurso: dizer
 * "seu perfil não permite alterar este usuário" a quem tentou excluir um posto seria
 * informação errada. O campo {@code error} do {@code ErrorResponse} continua
 * {@code PERFIL_NAO_PERMITIDO} nos dois casos, porque a ação corretiva é a mesma —
 * falar com quem tem o perfil.
 *
 * <p>Escrita e leitura têm mensagens distintas pelo mesmo motivo do P0.4c: hoje a regra
 * é idêntica para as duas, mas quem lê a mensagem precisa saber o que foi recusado.
 */
public final class AutorizacaoConstants {

    private AutorizacaoConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instanced");
    }

    public static final String PERFIL_NAO_PERMITIDO_ESCRITA_MESSAGE =
            "Seu perfil não permite criar, alterar ou excluir este registro";

    public static final String PERFIL_NAO_PERMITIDO_CONSULTA_MESSAGE =
            "Seu perfil não permite consultar este registro";
}
