package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
