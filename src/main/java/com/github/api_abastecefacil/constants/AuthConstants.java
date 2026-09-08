package com.github.api_abastecefacil.constants;

public final class AuthConstants {

    private AuthConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instanced");
    }

    public static final String TOKEN_TYPE = "Bearer";

    /**
     * Prefixo da authority do Spring Security. Combinado com o nome do perfil em
     * {@link com.github.api_abastecefacil.model.Perfil#authority()}, produz
     * {@code ROLE_<PERFIL>}, consumido por {@code hasRole("<PERFIL>")}.
     */
    public static final String ROLE_PREFIX = "ROLE_";

    /** Nome do claim de perfil dentro do JWT. */
    public static final String PERFIL_CLAIM = "perfil";

    public static final String USER_NOT_FOUND_MESSAGE = "Usuário não encontrado, por favor, verifique se o email está correto";
    public static final String USER_NOT_FOUND_BY_EMAIL_MESSAGE = "Usuário não encontrado com email: ";
    public static final String USER_INACTIVE_MESSAGE = "Usuário inativo, por favor, contate o suporte";
    public static final String INVALID_CREDENTIALS_MESSAGE = "Email ou senha incorretos";
    public static final String PASSWORD_NOT_SET_MESSAGE = "Usuário ainda não definiu uma senha, por favor, ative sua conta";

    public static final String LOGIN_SUCCESS_MESSAGE = "Login realizado com sucesso";
    public static final String ATIVACAO_SUCCESS_MESSAGE = "Conta ativada com sucesso";

    // ------------------------------------------------------------------------ S4

    /**
     * Resposta única de {@code POST /api/auth/recuperacao}.
     *
     * <p><b>A redação é a própria regra de segurança.</b> Ela é devolvida sem alteração
     * para e-mail inexistente, usuário inativo, usuário que nunca ativou a conta e
     * usuário normal — e por isso o texto é condicional ("se houver uma conta"), nunca
     * afirmativo: "enviamos um e-mail para você" confirmaria a existência da conta a
     * qualquer um que digitasse um endereço.
     */
    public static final String RECUPERACAO_SOLICITADA_MESSAGE =
            "Se houver uma conta com este e-mail, enviaremos as instruções de "
                    + "redefinição de senha em instantes. Verifique também a caixa de spam.";

    public static final String SENHA_REDEFINIDA_SUCCESS_MESSAGE = "Senha redefinida com sucesso";

    /**
     * Mensagem do 429. Genérica pelo mesmo motivo da anterior: quem pediu não pode
     * descobrir, pelo texto, se estourou o limite por e-mail ou por IP — o primeiro
     * confirmaria que aquele endereço vinha sendo tentado.
     */
    public static final String LIMITE_SOLICITACOES_EXCEDIDO_MESSAGE =
            "Muitas solicitações de redefinição de senha. Aguarde alguns minutos e "
                    + "tente novamente.";

}
