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
    public static final String USER_ALREADY_EXISTS_MESSAGE = "Já existe um usuário cadastrado com este email";
    public static final String USER_INACTIVE_MESSAGE = "Usuário inativo, por favor, contate o suporte";
    public static final String INVALID_CREDENTIALS_MESSAGE = "Email ou senha incorretos";

    public static final String REGISTER_SUCCESS_MESSAGE = "Usuário registrado com sucesso";
    public static final String LOGIN_SUCCESS_MESSAGE = "Login realizado com sucesso";

}
