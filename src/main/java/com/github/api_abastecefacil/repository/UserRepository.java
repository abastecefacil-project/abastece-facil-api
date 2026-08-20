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

    Page<User> findByIsActiveAndNameContainingIgnoreCase(Boolean active, String name, Pageable pageable);

    Long countByIsActiveTrue();

}
