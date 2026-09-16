package com.github.api_abastecefacil.model;

import com.github.api_abastecefacil.validation.UserValidator;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // Nullable desde a V4: o cadastro administrativo (S2) cria o usuario sem senha,
    // que e definida depois pelo fluxo de ativacao (S5). Ver senhaDefinida.
    @Column(name = "password")
    private String password;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false)
    private Perfil perfil;

    @ManyToOne
    @JoinColumn(name = "regional_id", foreignKey = @ForeignKey(name = "fk_users_regional"))
    private Regional regional;

    @Column(name = "telefone")
    private String telefone;

    // Unicidade garantida pelo indice parcial uk_users_matricula, criado na V4.
    // Nao ha @UniqueConstraint aqui de proposito: a anotacao descreveria uma
    // constraint total, que nao e o que existe no banco.
    @Column(name = "matricula")
    private String matricula;

    @Column(name = "senha_definida", nullable = false)
    private Boolean senhaDefinida;

    public Long getId() {
        return id;
    }

    public User setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public User setName(String name) {
        this.name = name;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public User setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public User setPassword(String password) {
        this.password = password;
        return this;
    }

    public Boolean getActive() {
        return isActive;
    }

    public User setActive(Boolean active) {
        isActive = active;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public User setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public User setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public User setPerfil(Perfil perfil) {
        this.perfil = perfil;
        return this;
    }

    public Regional getRegional() {
        return regional;
    }

    public User setRegional(Regional regional) {
        this.regional = regional;
        return this;
    }

    public String getTelefone() {
        return telefone;
    }

    public User setTelefone(String telefone) {
        this.telefone = telefone;
        return this;
    }

    public String getMatricula() {
        return matricula;
    }

    public User setMatricula(String matricula) {
        this.matricula = matricula;
        return this;
    }

    public Boolean getSenhaDefinida() {
        return senhaDefinida;
    }

    public User setSenhaDefinida(Boolean senhaDefinida) {
        this.senhaDefinida = senhaDefinida;
        return this;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        if (this.perfil == null) {
            this.perfil = Perfil.COLABORADOR;
        }
        if (this.senhaDefinida == null) {
            // Deny by default: sem senha, sem login.
            this.senhaDefinida = this.password != null;
        }
        this.telefone = UserValidator.normalizarTelefone(this.telefone);
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        this.telefone = UserValidator.normalizarTelefone(this.telefone);
    }

}
