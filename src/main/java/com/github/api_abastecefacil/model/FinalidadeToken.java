package com.github.api_abastecefacil.model;

/**
 * Finalidade de um token de acesso enviado por e-mail.
 *
 * <p>Faz parte do predicado que valida o token: um token de {@link #ATIVACAO} nunca é
 * aceito quando validado como {@link #RECUPERACAO}, e vice-versa. Sem isso, um link de
 * ativação vazado serviria para trocar a senha de uma conta já ativa.
 *
 * <p>O enum não carrega prazo de expiração de propósito. O tempo de vida é decisão
 * operacional, muda por ambiente e vive em {@code abastecefacil.token.*}; quem resolve
 * finalidade para prazo é o {@code TokenAcessoService}.
 */
public enum FinalidadeToken {

    ATIVACAO,
    RECUPERACAO
}
