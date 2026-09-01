package com.github.api_abastecefacil.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tokens_acesso",
        uniqueConstraints = @UniqueConstraint(name = "uk_tokens_acesso_token_hash", columnNames = "token_hash")
)
public class TokenAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sem @ManyToOne para User de proposito: o token e solicitado por e-mail e o
    // usuario pode nao existir no momento da solicitacao. Ver V5__token_acesso.sql.
    @Column(name = "email", nullable = false)
    private String email;

    // SHA-256 hexadecimal do token, 64 caracteres minusculos. O valor em claro NUNCA
    // e persistido: ele existe uma unica vez, no retorno de TokenAcessoService.gerarToken.
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "finalidade", nullable = false)
    private FinalidadeToken finalidade;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    // Consumo real pelo usuario. A invalidacao por substituicao NAO mexe aqui: ela
    // expira o token anterior, para que usado_em continue significando so uma coisa.
    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Column(name = "ip_solicitante")
    private String ipSolicitante;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public TokenAcesso setId(Long id) {
        this.id = id;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public TokenAcesso setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public TokenAcesso setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
        return this;
    }

    public FinalidadeToken getFinalidade() {
        return finalidade;
    }

    public TokenAcesso setFinalidade(FinalidadeToken finalidade) {
        this.finalidade = finalidade;
        return this;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public TokenAcesso setExpiraEm(LocalDateTime expiraEm) {
        this.expiraEm = expiraEm;
        return this;
    }

    public LocalDateTime getUsadoEm() {
        return usadoEm;
    }

    public TokenAcesso setUsadoEm(LocalDateTime usadoEm) {
        this.usadoEm = usadoEm;
        return this;
    }

    public String getIpSolicitante() {
        return ipSolicitante;
    }

    public TokenAcesso setIpSolicitante(String ipSolicitante) {
        this.ipSolicitante = ipSolicitante;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TokenAcesso setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    // Nao ha @PreUpdate, ao contrario das demais entidades: esta entidade nunca e
    // atualizada pela JPA. Consumo e invalidacao passam pelo UPDATE nativo do
    // TokenAcessoRepository, que a JPA nao intercepta -- um callback aqui nunca
    // dispararia. Ver o comentario de consumir() no repository.
    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
