package com.github.api_abastecefacil.config;

import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.github.api_abastecefacil.constants.AdministradorInicialConstants.*;

/**
 * Cria o administrador inicial na subida da aplicação, a partir de configuração.
 *
 * <p><b>Por que não é migration.</b> O Flyway roda fora do contexto de configuração do
 * Spring: uma migration não lê {@code @Value} nem propriedades, então não teria como
 * receber e-mail e hash externos. Ficaria obrigada a embutir os valores no SQL
 * versionado, que é exatamente o que não se pode fazer com credencial.
 *
 * <p><b>Por que não é endpoint.</b> Uma rota de bootstrap é uma rota que cria
 * administrador — e teria de ser pública para servir ao primeiro. A criação é por
 * infraestrutura.
 *
 * <p>É o primeiro {@code CommandLineRunner} do projeto, e roda depois de o Flyway ter
 * migrado e do contexto estar pronto. Idempotente por e-mail: subir N vezes cria no
 * máximo um usuário, e um e-mail já existente nunca é alterado.
 *
 * <p><b>O hash chega pronto e é gravado literalmente.</b> Este componente não injeta
 * {@code PasswordEncoder} de propósito: passar o valor por {@code encode} produziria o
 * BCrypt de um BCrypt, e o login falharia com um 401 que não se parece nada com erro de
 * configuração. Ver README, seção "Gerar o hash BCrypt".
 */
@Component
public class AdministradorInicialInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicialInitializer.class);

    private final UserRepository userRepository;
    private final String nome;
    private final String email;
    private final String senhaHash;

    // Resumo do erro, se houve, para ser repetido no ApplicationReadyEvent. Ver
    // resumirFalha().
    private String falha;

    public AdministradorInicialInitializer(
            UserRepository userRepository,
            @Value("${abastecefacil.admin.nome:}") String nome,
            @Value("${abastecefacil.admin.email:}") String email,
            @Value("${abastecefacil.admin.senha-hash:}") String senhaHash) {
        this.userRepository = userRepository;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    @Override
    public void run(String... args) {
        if (isBlank(email) || isBlank(senhaHash)) {
            // Caminho normal em desenvolvimento: sem configuração, nao se cria nada e a
            // aplicacao sobe igual. Nao e erro, entao e INFO e nao alimenta o resumo.
            log.info(CONFIG_AUSENTE_MESSAGE);
            return;
        }

        if (!BCRYPT_PATTERN.matcher(senhaHash).matches()) {
            // Sem interpolar o valor recebido: ver HASH_INVALIDO_MESSAGE.
            log.error(HASH_INVALIDO_MESSAGE);
            this.falha = RESUMO_HASH_INVALIDO;
            return;
        }

        Optional<User> existente = userRepository.findByEmail(email);
        if (existente.isPresent()) {
            registrarEmailJaUsado(existente.get());
            return;
        }

        criarAdministrador();
    }

    /**
     * Repete o erro como última linha de log do Spring.
     *
     * <p>O {@code ApplicationReadyEvent} é publicado depois de todos os
     * {@code CommandLineRunner}, então este resumo sai atrás do ERROR detalhado e de
     * qualquer outro ruído de subida. Existe porque log de inicialização é longo e um
     * ERROR no meio dele passa despercebido com facilidade — e uma configuração de
     * administrador que falhou em silêncio só é descoberta na hora de logar.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumirFalha() {
        if (falha != null) {
            log.error(RESUMO_FALHA_MESSAGE, falha);
        }
    }

    private void registrarEmailJaUsado(User usuario) {
        if (Perfil.ADMINISTRADOR.equals(usuario.getPerfil())) {
            log.info(JA_EXISTE_MESSAGE, email);
            return;
        }

        // Caso real e provavel: admin@abastecefacil.com vem do init-scripts/dump.sql com
        // perfil COLABORADOR e hash de origem desconhecida. Pular em silencio deixaria a
        // pessoa achando que tem um administrador. Nada e alterado -- os usuarios do dump
        // sao preservados.
        log.warn(EMAIL_OCUPADO_MESSAGE, email, usuario.getPerfil());
    }

    private void criarAdministrador() {
        User administrador = new User()
                .setName(isBlank(nome) ? NOME_PADRAO : nome)
                .setEmail(email)
                // Literal, sem passar por PasswordEncoder. Ver o javadoc da classe.
                .setPassword(senhaHash)
                .setPerfil(Perfil.ADMINISTRADOR)
                .setActive(true)
                .setSenhaDefinida(true)
                .setRegional(null)
                .setMatricula(null);

        try {
            userRepository.save(administrador);
        } catch (DataIntegrityViolationException e) {
            // findByEmail seguido de save tem janela de corrida entre ler e escrever: com
            // duas instancias subindo juntas, as duas leriam vazio e as duas tentariam
            // inserir. Quem perder bate na constraint unica de users.email, que e o
            // backstop real da idempotencia. Um CommandLineRunner que lanca DERRUBA a
            // subida, entao aqui o erro vira log e a aplicacao segue.
            log.info(CORRIDA_MESSAGE, email);
            return;
        }

        // O e-mail vai para o log de proposito: nao e segredo e a auditoria precisa saber
        // qual conta administrativa nasceu. O hash, nunca.
        log.info(CRIADO_MESSAGE, email);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
