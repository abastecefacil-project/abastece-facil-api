package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.model.NotificacaoNeo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificacaoNeoRepository extends JpaRepository<NotificacaoNeo, Long> {
    Page<NotificacaoNeo> findByAdministradorIdOrderByCriadoEmDescIdDesc(Long administradorId, Pageable pageable);
    long countByAdministradorIdAndLidaFalse(Long administradorId);

    @Modifying
    @Query(value = """
            INSERT INTO notificacoes_neo (administrador_id, usuario_id, posto_id, usuario_nome, posto_nome)
            SELECT a.id, u.id, p.id, u.name, COALESCE(NULLIF(BTRIM(p.fantasy_name), ''), p.name)
            FROM users a JOIN users u ON u.id = :usuarioId JOIN gas_stations p ON p.id = :postoId
            WHERE a.perfil = 'ADMINISTRADOR' AND a.is_active = true
            """, nativeQuery = true)
    void notificarRecusa(@Param("postoId") Long postoId, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificacaoNeo n SET n.lida = true WHERE n.id = :id AND n.administradorId = :administradorId")
    int marcarLida(@Param("id") Long id, @Param("administradorId") Long administradorId);
}
