package com.github.api_abastecefacil.validation;

import com.github.api_abastecefacil.exception.InvalidUserDataException;

import java.util.regex.Pattern;

import static com.github.api_abastecefacil.constants.UserConstants.MATRICULA_INVALIDA_MESSAGE;
import static com.github.api_abastecefacil.constants.UserConstants.TELEFONE_INVALIDO_MESSAGE;

/**
 * Validações de domínio dos campos de identidade do usuário.
 *
 * <p>Validar e normalizar são operações separadas de propósito:
 * {@link #validarTelefone(String)} apenas rejeita formato inválido, e
 * {@link #normalizarTelefone(String)} é função pura que não valida nada. Assim o
 * callback de persistência da entidade pode normalizar sem risco de lançar, e o
 * endpoint pode validar sem alterar o valor recebido.
 *
 * <p>Escopo deliberado do formato de telefone: <b>DDI não é aceito</b>
 * ({@code +55 47 ...} é rejeitado — aqui "formato brasileiro" é o formato nacional)
 * e a regra do nono dígito <b>não é imposta</b>, para evitar falso negativo.
 * Apertar qualquer uma das duas depois é alteração aditiva.
 */
public final class UserValidator {

    private UserValidator() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    private static final Pattern MATRICULA_PATTERN = Pattern.compile("^\\d{4,12}$");

    /**
     * Aceita com ou sem máscara: 47999998888, (47) 99999-8888, 47 3422-1234,
     * (47)3422-1234, 4734221234. Total de dígitos: 10 (fixo) ou 11 (celular).
     */
    private static final Pattern TELEFONE_PATTERN =
            Pattern.compile("^\\(?\\d{2}\\)?[\\s-]?\\d{4,5}-?\\d{4}$");

    private static final Pattern APENAS_DIGITOS = Pattern.compile("\\D");

    /** Nulo é aceito: matrícula é opcional. */
    public static void validarMatricula(String matricula) {
        if (matricula == null) {
            return;
        }
        if (!MATRICULA_PATTERN.matcher(matricula).matches()) {
            throw new InvalidUserDataException(MATRICULA_INVALIDA_MESSAGE);
        }
    }

    /** Nulo é aceito: telefone é opcional. */
    public static void validarTelefone(String telefone) {
        if (telefone == null) {
            return;
        }
        if (!TELEFONE_PATTERN.matcher(telefone).matches()) {
            throw new InvalidUserDataException(TELEFONE_INVALIDO_MESSAGE);
        }
    }

    /**
     * Função pura: devolve apenas os dígitos do telefone, sem validar formato.
     * Nulo, ou string sem dígito nenhum, viram {@code null} — para não gravar
     * string vazia no banco. Idempotente.
     */
    public static String normalizarTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = APENAS_DIGITOS.matcher(telefone).replaceAll("");
        return digitos.isEmpty() ? null : digitos;
    }
}
