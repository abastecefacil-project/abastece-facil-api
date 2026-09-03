package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.model.TokenAcesso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Primeiro repository do projeto com {@code @Modifying}: os outros tres
 * {@code @Query} existentes sao leitura paginada.
 *
 * <p>Todas as escritas recebem a finalidade como {@code String}, e nao como
 * {@code FinalidadeToken}. Em query nativa o Hibernate binda um enum pelo
 * <strong>ordinal</strong>, nao pelo nome, e o inteiro nunca casaria com o
 * {@code character varying} da coluna: o UPDATE afetaria zero linhas em todo caso e
 * a validacao rejeitaria silenciosamente todo token valido. Quem chama passa
 * {@code finalidade.name()}.
 */
@Repository
public interface TokenAcessoRepository extends JpaRepository<TokenAcesso, Long> {

    Optional<TokenAcesso> findByTokenHash(String tokenHash);

    // Consumo atomico: uma unica instrucao decide e marca.
    //
    // A alternativa obvia -- findByTokenHash seguido de save -- tem janela de corrida
    // entre ler e escrever: duas requisicoes simultaneas com o mesmo token leriam as
    // duas usado_em = null e as duas passariam, e o token de uso unico valeria duas
    // vezes. O UPDATE condicional fecha a janela porque o Postgres serializa a escrita
    // na linha: a segunda requisicao so reavalia o predicado depois que a primeira
    // comita, ja com usado_em preenchido, e afeta 0 linhas. Por isso o token so e
    // tratado como valido quando a contagem de linhas afetadas e exatamente 1.
    //
    // O mesmo predicado cobre as quatro rejeicoes de uma vez: inexistente (token_hash
    // nao casa), ja usado (usado_em IS NULL falha), expirado e finalidade divergente.
    //
    // flush/clearAutomatically: o service le o e-mail logo depois deste UPDATE, e a
    // leitura nao pode vir do cache de primeiro nivel, que nao enxerga escrita nativa.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE tokens_acesso
               SET usado_em = now()
             WHERE token_hash = :tokenHash
               AND finalidade = :finalidade
               AND usado_em IS NULL
               AND expira_em > now()
            """, nativeQuery = true)
    int consumir(@Param("tokenHash") String tokenHash, @Param("finalidade") String finalidade);

    // Leitura nao destrutiva, para a sonda GET /api/auth/ativacao/validar decidir entre
    // exibir o formulario de senha e a tela de link expirado.
    //
    // O predicado e IDENTICO ao de consumir(), e os dois precisam continuar assim: sao a
    // mesma definicao de "token valido" e divergir faria a sonda dizer valido para um
    // token que o consumo rejeita, ou o contrario. Ficam lado a lado neste arquivo por
    // isso, e a validade e avaliada em SQL nos dois -- com now() do banco, sem depender
    // do relogio da aplicacao.
    //
    // Nao e @Modifying: nao marca usado_em, nao mexe em expira_em, nao consome nada.
    // Chamar a sonda N vezes deixa o token exatamente como estava.
    @Query(value = """
            SELECT email
              FROM tokens_acesso
             WHERE token_hash = :tokenHash
               AND finalidade = :finalidade
               AND usado_em IS NULL
               AND expira_em > now()
            """, nativeQuery = true)
    Optional<String> findEmailDeTokenValido(@Param("tokenHash") String tokenHash,
                                            @Param("finalidade") String finalidade);

    // Invalidacao por substituicao: expira o token anterior em vez de marca-lo usado.
    //
    // usado_em fica significando apenas consumo real pelo usuario, que e o que a
    // auditoria precisa distinguir. Quem ler a tabela depois nao deve confundir esta
    // expiracao forcada com TTL natural: o token nao venceu sozinho, foi substituido
    // por um reenvio. expira_em = now() ja falha o predicado expira_em > now() do
    // consumo, porque now() no Postgres e o timestamp de inicio da transacao.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE tokens_acesso
               SET expira_em = now()
             WHERE email = :email
               AND finalidade = :finalidade
               AND usado_em IS NULL
               AND expira_em > now()
            """, nativeQuery = true)
    int invalidarPendentes(@Param("email") String email, @Param("finalidade") String finalidade);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM tokens_acesso WHERE expira_em < :limite", nativeQuery = true)
    int deleteExpiradosAntesDe(@Param("limite") LocalDateTime limite);
}
