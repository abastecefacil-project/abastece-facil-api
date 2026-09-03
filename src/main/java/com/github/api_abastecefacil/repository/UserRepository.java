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

}
