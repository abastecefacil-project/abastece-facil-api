package com.github.api_abastecefacil.validation;

import com.github.api_abastecefacil.exception.DominioEmailNaoPermitidoException;
import com.github.api_abastecefacil.exception.InvalidUserDataException;
import com.github.api_abastecefacil.exception.SenhaFracaException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.github.api_abastecefacil.constants.UserConstants.*;

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

    /** {@code \p{L}} e nao [a-zA-Z]: acentuada tambem conta como letra. */
    private static final Pattern CONTEM_LETRA = Pattern.compile("\\p{L}");

    private static final Pattern CONTEM_DIGITO = Pattern.compile("\\d");

    /** Separa o nome em palavras por espaco em branco de qualquer tipo. */
    private static final Pattern SEPARADOR_NOME = Pattern.compile("\\s+");

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
     * Política de senha do S3, validada no backend porque o frontend é sugestão e não
     * garantia: a mesma requisição chega de curl sem passar por formulário nenhum.
     *
     * <p>Duas regras, duas mensagens, <b>um só código de erro</b>
     * ({@code SENHA_FRACA}): comprimento e composição de um lado; conter nome ou e-mail
     * do outro.
     *
     * <p><b>A senha recebida nunca é registrada</b> — não entra na mensagem, na exceção
     * nem em log, inclusive no caminho de rejeição. Mesmo cuidado do A3 com o hash do
     * administrador e do M3 com a chave do Resend.
     *
     * @param senha senha em claro, recém-digitada
     * @param email e-mail do dono da conta, para barrar senha derivada dele
     * @param nome  nome do dono da conta, idem
     */
    public static void validarSenha(String senha, String email, String nome) {
        if (senha == null
                || senha.length() < SENHA_TAMANHO_MINIMO
                || !CONTEM_LETRA.matcher(senha).find()
                || !CONTEM_DIGITO.matcher(senha).find()) {
            throw new SenhaFracaException(SENHA_FRACA_MESSAGE);
        }

        if (contemDadoPessoal(senha, email, nome)) {
            throw new SenhaFracaException(SENHA_COM_DADOS_PESSOAIS_MESSAGE);
        }
    }

    /**
     * Compara em caixa baixa contra: o e-mail inteiro, a parte antes do último arroba, e
     * cada palavra do nome com {@link com.github.api_abastecefacil.constants.UserConstants#SENHA_NOME_PALAVRA_MINIMA}
     * caracteres ou mais.
     *
     * <p>A parte local do e-mail entra separada porque é ela que costuma virar senha
     * ({@code jsilva2024}), e o e-mail inteiro quase nunca aparece por completo.
     */
    private static boolean contemDadoPessoal(String senha, String email, String nome) {
        String alvo = senha.toLowerCase(Locale.ROOT);

        return termosProibidos(email, nome).anyMatch(alvo::contains);
    }

    private static Stream<String> termosProibidos(String email, String nome) {
        // O e-mail inteiro entra sem limiar: e longo o bastante para nunca casar por
        // acidente.
        Stream<String> enderecoCompleto = Stream.of(email)
                .filter(Objects::nonNull)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .filter(v -> !v.isEmpty());

        // A parte local, sim, respeita o mesmo limiar das palavras do nome, e pelo mesmo
        // motivo: um endereco como ana@fiesc.org.br reprovaria "banana123456", que nao
        // tem nada de pessoal.
        Stream<String> local = Stream.of(parteLocal(email))
                .filter(Objects::nonNull)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .filter(v -> v.length() >= SENHA_NOME_PALAVRA_MINIMA);

        Stream<String> palavrasDoNome = nome == null
                ? Stream.empty()
                : Arrays.stream(SEPARADOR_NOME.split(nome))
                .map(v -> v.toLowerCase(Locale.ROOT))
                .filter(v -> v.length() >= SENHA_NOME_PALAVRA_MINIMA);

        return Stream.concat(enderecoCompleto, Stream.concat(local, palavrasDoNome));
    }

    private static String parteLocal(String email) {
        if (email == null) {
            return null;
        }
        int ultimoArroba = email.lastIndexOf('@');
        return ultimoArroba <= 0 ? email : email.substring(0, ultimoArroba);
    }

    /**
     * Rejeita e-mail cujo domínio não esteja na lista autorizada.
     *
     * <p><b>A comparação é sobre a parte depois do ÚLTIMO arroba</b>, nunca sobre o e-mail
     * inteiro. Um {@code contains} sobre a string toda aceitaria
     * {@code fiesc.org.br@gmail.com}, onde o domínio permitido aparece como nome de
     * usuário — o endereço é do Gmail e o texto casa mesmo assim.
     *
     * <p>Aceita <b>igualdade exata ou subdomínio</b>. O ponto em {@code "." + permitido}
     * não é enfeite: sem ele, {@code notfiesc.org.br} terminaria em {@code fiesc.org.br} e
     * passaria. E como o domínio comparado é o trecho final inteiro,
     * {@code contato@fiesc.org.br.exemplo.com} também é rejeitado — ali o domínio real é
     * {@code exemplo.com}, e {@code fiesc.org.br} é só um rótulo no meio.
     *
     * <p>A lista vem por parâmetro, e não de configuração injetada, para esta classe
     * continuar sendo utilitária e pura como as irmãs. Quem lê
     * {@code abastecefacil.auth.dominios-permitidos} é o {@code UserService}.
     */
    public static void validarDominioEmail(String email, List<String> dominiosPermitidos) {
        String dominio = extrairDominio(email);

        boolean permitido = dominiosPermitidos != null && dominiosPermitidos.stream()
                .filter(Objects::nonNull)
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .anyMatch(d -> dominio.equals(d) || dominio.endsWith("." + d));

        if (!permitido) {
            throw new DominioEmailNaoPermitidoException(String.format(
                    DOMINIO_EMAIL_NAO_PERMITIDO_MESSAGE, String.join(", ", listaSegura(dominiosPermitidos))));
        }
    }

    /**
     * Devolve a parte depois do último arroba, em caixa baixa. String sem arroba, ou
     * terminada nele, não tem domínio e por isso nunca pode ser autorizada — devolver
     * vazio faz o predicado de {@link #validarDominioEmail} rejeitar naturalmente, sem
     * um segundo caminho de erro.
     *
     * <p>O {@code @Email} do Bean Validation já barra a maior parte disso antes de chegar
     * aqui; esta guarda existe porque o método é público e estático, e pode ser chamado
     * de onde não houve validação nenhuma.
     */
    private static String extrairDominio(String email) {
        if (email == null) {
            return "";
        }
        int ultimoArroba = email.lastIndexOf('@');
        if (ultimoArroba < 0 || ultimoArroba == email.length() - 1) {
            return "";
        }
        return email.substring(ultimoArroba + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> listaSegura(List<String> dominios) {
        return dominios == null ? List.of() : dominios;
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
