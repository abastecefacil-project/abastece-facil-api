package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.dto.dashboard.ContagemAgrupada;
import com.github.api_abastecefacil.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Matrícula é única apenas entre os não nulos, garantida pelo índice parcial
     * {@code uk_users_matricula} da V4. Este método só é chamado com matrícula não nula —
     * chamá-lo com {@code null} devolveria {@code false} sempre, já que {@code = NULL}
     * nunca casa em SQL, o que é correto mas inútil.
     */
    boolean existsByMatricula(String matricula);

    Page<User> findByIsActiveAndNameContainingIgnoreCase(Boolean active, String name, Pageable pageable);

    Long countByIsActiveTrue();

    /**
     * Listagem escopada a uma regional, para o GESTOR_FROTA.
     *
     * <p>{@code RegionalId} atravessa o {@code @ManyToOne} ate {@code regional.id}, entao
     * a consulta nao carrega a entidade Regional para comparar. Usuario com
     * {@code regional_id} nulo nunca casa, o que e o desejado: ele nao pertence a regional
     * nenhuma.
     */
    Page<User> findByIsActiveAndRegionalIdAndNameContainingIgnoreCase(
            Boolean active, Long regionalId, String name, Pageable pageable);

    /**
     * Listagem de um unico usuario, para o COLABORADOR -- que so enxerga a si mesmo -- e
     * para o gestor sem regional, que nao tem escopo com que filtrar.
     *
     * <p>Devolve Page, e nao Optional, de proposito: o endpoint contrata Page e os
     * filtros {@code active} e {@code name} continuam valendo, entao o proprio usuario
     * some da lista se nao casar com eles. Montar a pagina na mao no service
     * reimplementaria isso pior.
     */
    Page<User> findByIsActiveAndIdAndNameContainingIgnoreCase(
            Boolean active, Long id, String name, Pageable pageable);

    // Contagens do resumo da Home, uma por escopo de EscopoUsuarios -- o escopo Todos usa
    // o countByIsActiveTrue acima. "Aguardando ativacao" e ativo sem senha definida: o
    // usuario criado pelo cadastro administrativo que ainda nao abriu o convite.

    long countByIsActiveTrueAndSenhaDefinidaFalse();

    long countByIsActiveTrueAndRegionalId(Long regionalId);

    long countByIsActiveTrueAndSenhaDefinidaFalseAndRegionalId(Long regionalId);

    long countByIsActiveTrueAndId(Long id);

    long countByIsActiveTrueAndSenhaDefinidaFalseAndId(Long id);

    // Convites pendentes "antigos": criados antes do limite. created_at e gravado no fuso
    // da JVM (LocalDateTime.now()), e o limite chega calculado em America/Sao_Paulo.

    long countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBefore(LocalDateTime limite);

    long countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndRegionalId(
            LocalDateTime limite, Long regionalId);

    long countByIsActiveTrueAndSenhaDefinidaFalseAndCreatedAtBeforeAndId(
            LocalDateTime limite, Long id);

    // Ativos por perfil, uma consulta por escopo. O perfil volta como texto da coluna -- em
    // query nativa o enum nao e convertido -- e o service faz Perfil.valueOf. Perfil sem
    // usuario nao aparece; quem preenche o zero e o service.

    @Query(value = """
            SELECT u.perfil AS chave, COUNT(*) AS total
              FROM users u
             WHERE u.is_active = true
             GROUP BY u.perfil
            """, nativeQuery = true)
    List<ContagemAgrupada> contarAtivosPorPerfil();

    @Query(value = """
            SELECT u.perfil AS chave, COUNT(*) AS total
              FROM users u
             WHERE u.is_active = true
               AND u.regional_id = :regionalId
             GROUP BY u.perfil
            """, nativeQuery = true)
    List<ContagemAgrupada> contarAtivosPorPerfilNaRegional(@Param("regionalId") Long regionalId);

    @Query(value = """
            SELECT u.perfil AS chave, COUNT(*) AS total
              FROM users u
             WHERE u.is_active = true
               AND u.id = :id
             GROUP BY u.perfil
            """, nativeQuery = true)
    List<ContagemAgrupada> contarAtivosPorPerfilDoUsuario(@Param("id") Long id);
}
