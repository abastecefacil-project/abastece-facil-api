package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.exception.TokenInvalidoException;
import com.github.api_abastecefacil.model.FinalidadeToken;
import com.github.api_abastecefacil.model.TokenAcesso;
import com.github.api_abastecefacil.repository.TokenAcessoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static com.github.api_abastecefacil.constants.TokenAcessoConstants.*;

/**
 * Emissão e consumo de tokens de acesso de uso único enviados por e-mail.
 *
 * <p>O token em claro existe em exatamente um lugar: o valor de retorno de
 * {@link #gerarToken}. Ele não é persistido, não é logado — nem em debug — e não entra
 * em mensagem de exceção. O banco guarda apenas o SHA-256 dele, de forma que uma
 * consulta direta a {@code tokens_acesso} não devolve nada que sirva para autenticar.
 */
@Service
@Transactional(readOnly = true)
public class TokenAcessoService {

    private static final Logger log = LoggerFactory.getLogger(TokenAcessoService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenAcessoRepository tokenAcessoRepository;
    private final long ativacaoHoras;
    private final long recuperacaoHoras;

    public TokenAcessoService(
            TokenAcessoRepository tokenAcessoRepository,
            @Value("${abastecefacil.token.ativacao-horas}") long ativacaoHoras,
            @Value("${abastecefacil.token.recuperacao-horas}") long recuperacaoHoras) {
        this.tokenAcessoRepository = tokenAcessoRepository;
        this.ativacaoHoras = ativacaoHoras;
        this.recuperacaoHoras = recuperacaoHoras;
    }

    /**
     * Gera um token novo para o par (e-mail, finalidade), invalidando os pendentes
     * anteriores, e devolve o valor em claro.
     *
     * <p>O retorno é a única aparição do token em claro no sistema. Quem chama pode
     * montá-lo num link de e-mail e nada mais: não guardar, não logar, não devolver
     * em DTO.
     */
    @Transactional
    public String gerarToken(String email, FinalidadeToken finalidade, String ipSolicitante) {
        // Antes de emitir o novo: um reenvio invalida o link anterior, senao dois
        // links validos circulariam para a mesma finalidade.
        tokenAcessoRepository.invalidarPendentes(email, finalidade.name());

        String tokenEmClaro = sortearToken();

        TokenAcesso token = new TokenAcesso()
                .setEmail(email)
                .setTokenHash(sha256Hex(tokenEmClaro))
                .setFinalidade(finalidade)
                .setExpiraEm(LocalDateTime.now().plusHours(resolverHoras(finalidade)))
                .setIpSolicitante(ipSolicitante);

        tokenAcessoRepository.save(token);

        return tokenEmClaro;
    }

    /**
     * Valida o token consumindo-o e devolve o e-mail a que ele pertence.
     *
     * <p>Token inexistente, expirado, já usado ou de finalidade divergente são
     * rejeitados com a mesma {@link TokenInvalidoException} e a mesma mensagem, para
     * não revelar qual das quatro condições ocorreu.
     */
    @Transactional
    public String validarEConsumir(String tokenEmClaro, FinalidadeToken finalidade) {
        String tokenHash = sha256Hex(tokenEmClaro);

        // O consumo e o proprio teste de validade: um UPDATE condicional que so afeta
        // a linha se ela existir, estiver nao usada, dentro do prazo e com a finalidade
        // certa. Qualquer contagem diferente de 1 significa rejeicao. Ver consumir()
        // no repository para o porque de nao ser find + save.
        int linhasAfetadas = tokenAcessoRepository.consumir(tokenHash, finalidade.name());

        if (linhasAfetadas != 1) {
            throw new TokenInvalidoException(TOKEN_INVALIDO_MESSAGE);
        }

        // A corrida ja foi vencida acima: esta leitura so recupera o e-mail da linha
        // que este chamador, e nenhum outro, marcou como usada.
        return tokenAcessoRepository.findByTokenHash(tokenHash)
                .map(TokenAcesso::getEmail)
                .orElseThrow(() -> new TokenInvalidoException(TOKEN_INVALIDO_MESSAGE));
    }

    /**
     * Diz de quem é o token, <b>sem consumi-lo</b>, e devolve vazio se ele não for
     * válido.
     *
     * <p>Existe para a sonda do S3: o frontend precisa saber se mostra o formulário de
     * senha ou a tela de link expirado, e usar {@link #validarEConsumir} para isso
     * queimaria o token só de abrir a página.
     *
     * <p><b>Não lança.</b> As quatro rejeições — inexistente, expirado, já usado e de
     * finalidade divergente — devolvem o mesmo {@code Optional.empty()}, sem distinção,
     * pela mesma razão da mensagem única de {@link #validarEConsumir}.
     *
     * <p>Isto é uma leitura otimista, <b>não</b> a autoridade sobre a validade: entre
     * esta consulta e o consumo o token pode ser usado por outra requisição. Quem decide
     * continua sendo o {@code UPDATE} condicional de {@link #validarEConsumir}.
     */
    public Optional<String> emailDeTokenValido(String tokenEmClaro, FinalidadeToken finalidade) {
        return tokenAcessoRepository.findEmailDeTokenValido(sha256Hex(tokenEmClaro), finalidade.name());
    }

    /**
     * Remove diariamente os tokens expirados há mais de
     * {@value com.github.api_abastecefacil.constants.TokenAcessoConstants#DIAS_RETENCAO_EXPIRADOS}
     * dias. A janela de retenção existe para que um token recém-vencido ainda apareça
     * numa investigação de suporte.
     */
    @Transactional
    @Scheduled(cron = CRON_LIMPEZA)
    public void limparTokensExpirados() {
        LocalDateTime limite = LocalDateTime.now().minusDays(DIAS_RETENCAO_EXPIRADOS);

        int removidos = tokenAcessoRepository.deleteExpiradosAntesDe(limite);

        // Apenas a contagem: nem o hash nem o e-mail vao para o log.
        log.info("Limpeza de tokens de acesso: {} registro(s) expirado(s) removido(s)", removidos);
    }

    /**
     * Concentra aqui a única ramificação por finalidade. O prazo é decisão
     * operacional, vem de {@code abastecefacil.token.*} e nenhum chamador precisa
     * conhecê-lo.
     */
    private long resolverHoras(FinalidadeToken finalidade) {
        return switch (finalidade) {
            case ATIVACAO -> ativacaoHoras;
            case RECUPERACAO -> recuperacaoHoras;
        };
    }

    /**
     * Sorteia um token opaco de {@value com.github.api_abastecefacil.constants.TokenAcessoConstants#TOKEN_BYTES}
     * bytes com {@link SecureRandom}. A codificação é Base64 URL-safe sem padding
     * porque o valor viaja dentro de um link de e-mail, onde {@code +}, {@code /} e
     * {@code =} exigiriam escape.
     */
    private String sortearToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 em hexadecimal minúsculo, 64 caracteres.
     *
     * <p>SHA-256 e não BCrypt de propósito. O custo deliberado do BCrypt existe para
     * senhas escolhidas por pessoas, que têm pouca entropia e são atacáveis por
     * dicionário. Este token tem 256 bits sorteados por {@link SecureRandom}: não há
     * força bruta viável a proteger, e o hash é recalculado a cada validação, então o
     * custo seria desperdício puro.
     */
    private static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITMO_HASH);
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e obrigatorio em toda JVM. Se faltar, nao ha recuperacao sensata.
            throw new IllegalStateException("Algoritmo de hash indisponível: " + ALGORITMO_HASH, e);
        }
    }
}
