package com.github.api_abastecefacil.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacoes_neo")
public class NotificacaoNeo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "administrador_id", nullable = false)
    private Long administradorId;
    @Column(name = "posto_id", nullable = false)
    private Long postoId;
    @Column(name = "usuario_nome", nullable = false)
    private String usuarioNome;
    @Column(name = "posto_nome", nullable = false)
    private String postoNome;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
    @Column(nullable = false)
    private boolean lida;

    public Long getId() { return id; }
    public Long getPostoId() { return postoId; }
    public String getUsuarioNome() { return usuarioNome; }
    public String getPostoNome() { return postoNome; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public boolean isLida() { return lida; }
}
