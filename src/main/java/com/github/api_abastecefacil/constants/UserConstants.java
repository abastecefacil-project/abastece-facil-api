package com.github.api_abastecefacil.constants;

public class UserConstants {
    private UserConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final String USER_NOT_FOUND_MESSAGE = "Usuário não encontrado";
    public static final String USER_ALREADY_DELETED_MESSAGE = "Usuário deletado";
    public static final String EMAIL_ALREADY_EXISTS_MESSAGE = "Já existe um usuário cadastrado com esse e-mail";
    public static final String MATRICULA_INVALIDA_MESSAGE = "Matrícula deve conter de 4 a 12 dígitos";
    public static final String TELEFONE_INVALIDO_MESSAGE = "Telefone inválido. Use o formato (47) 99999-8888";
}
