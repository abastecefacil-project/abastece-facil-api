package com.github.api_abastecefacil.tools;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.api_abastecefacil.constants.AdministradorInicialConstants.BCRYPT_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ferramenta para gerar o hash BCrypt do administrador inicial, publicada como teste
 * porque o projeto já tem Spring Security no classpath e nenhum plugin capaz de rodar
 * uma classe avulsa — e acrescentar um seria dependência nova.
 *
 * <p>Está em {@code tools} e não em {@code service} para deixar claro que a função
 * principal desta classe é ser executável sob demanda, não cobrir código.
 *
 * <p>Uso, em PowerShell (ver README, seção "Gerar o hash BCrypt"):
 *
 * <pre>
 * $env:BCRYPT_SENHA = 'a-senha-escolhida'
 * ./mvnw test -Dtest=GerarHashBCryptTest
 * Get-Content target/hash-bcrypt.txt
 * Remove-Item Env:\BCRYPT_SENHA
 * </pre>
 *
 * <p>A senha entra por variável de ambiente e o hash sai em arquivo dentro de
 * {@code target/}, que o {@code .gitignore} já cobre. Nada é impresso no console: a
 * saída padrão de um build costuma acabar em log de CI ou em scroll de terminal, e o
 * hash não precisa passar por lá.
 *
 * <p>Sem a variável definida, o teste roda apenas a verificação de sanidade abaixo e
 * passa em silêncio, para que um {@code ./mvnw clean test} normal continue verde.
 */
class GerarHashBCryptTest {

    private static final String VARIAVEL_SENHA = "BCRYPT_SENHA";
    private static final Path ARQUIVO_SAIDA = Path.of("target", "hash-bcrypt.txt");

    /**
     * Amarra o formato aceito por
     * {@link com.github.api_abastecefacil.constants.AdministradorInicialConstants#BCRYPT_PATTERN}
     * ao que o {@code BCryptPasswordEncoder} de fato produz. Se um dia a validação do
     * inicializador e o gerador divergirem, isto quebra aqui e não no login.
     */
    @Test
    void bcrypt_ShouldProduceAHashThatMatchesTheValidationPattern() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String valorDeTeste = "valor-de-teste-sem-uso-real";

        String hash = encoder.encode(valorDeTeste);

        assertThat(hash).matches(BCRYPT_PATTERN.pattern());
        assertThat(hash).hasSize(60);
        assertThat(encoder.matches(valorDeTeste, hash)).isTrue();
        assertThat(encoder.matches("outro-valor", hash)).isFalse();
    }

    @Test
    void gerarHash_ShouldWriteHashToFile_WhenEnvironmentVariableIsSet() throws IOException {
        String senha = System.getenv(VARIAVEL_SENHA);

        if (senha == null || senha.trim().isEmpty()) {
            // Caminho normal da suite: nada a fazer, nada escrito, teste passa.
            return;
        }

        String hash = new BCryptPasswordEncoder().encode(senha);

        Files.createDirectories(ARQUIVO_SAIDA.getParent());
        Files.writeString(ARQUIVO_SAIDA, hash, StandardCharsets.UTF_8);

        // Assertions sobre o hash, nunca sobre a senha, e sem imprimir nenhum dos dois.
        assertThat(hash).matches(BCRYPT_PATTERN.pattern());
        assertThat(Files.readString(ARQUIVO_SAIDA, StandardCharsets.UTF_8)).isEqualTo(hash);
    }
}
