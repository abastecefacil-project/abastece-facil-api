package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.dto.gasStation.AvaliacaoPosto;
import com.github.api_abastecefacil.dto.gasStation.ResumoAvaliacaoPosto;
import com.github.api_abastecefacil.model.GasStation;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface AvaliacaoPostoRepository extends Repository<GasStation, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO avaliacoes_postos (posto_id, usuario_id, nota, comentario)
            VALUES (:postoId, :usuarioId, :nota, :comentario)
            ON CONFLICT (posto_id, usuario_id) DO UPDATE
            SET nota = EXCLUDED.nota, comentario = EXCLUDED.comentario, atualizado_em = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void salvar(@Param("postoId") Long postoId, @Param("usuarioId") Long usuarioId,
                @Param("nota") int nota, @Param("comentario") String comentario);

    @Modifying
    @Query(value = "DELETE FROM avaliacoes_postos WHERE posto_id = :postoId AND usuario_id = :usuarioId", nativeQuery = true)
    void remover(@Param("postoId") Long postoId, @Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT u.name AS autor, a.nota, a.comentario, a.atualizado_em AS "atualizadoEm"
            FROM avaliacoes_postos a JOIN users u ON u.id = a.usuario_id
            WHERE a.posto_id = :postoId AND a.usuario_id = :usuarioId
            """, nativeQuery = true)
    Optional<AvaliacaoPosto> buscarMinha(@Param("postoId") Long postoId, @Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT u.name AS autor, a.nota, a.comentario, a.atualizado_em AS "atualizadoEm"
            FROM avaliacoes_postos a JOIN users u ON u.id = a.usuario_id
            WHERE a.posto_id = :postoId ORDER BY a.atualizado_em DESC, a.usuario_id DESC
            """, countQuery = "SELECT COUNT(*) FROM avaliacoes_postos WHERE posto_id = :postoId", nativeQuery = true)
    Page<AvaliacaoPosto> listar(@Param("postoId") Long postoId, Pageable pageable);

    @Query(value = """
            SELECT posto_id AS "postoId", AVG(nota) AS media, COUNT(*) AS total
            FROM avaliacoes_postos GROUP BY posto_id
            """, nativeQuery = true)
    List<ResumoAvaliacaoPosto> resumos();

    @Query(value = "SELECT AVG(nota) AS media, COUNT(*) AS total FROM avaliacoes_postos WHERE posto_id = :postoId", nativeQuery = true)
    ResumoAvaliacaoPosto resumo(@Param("postoId") Long postoId);
}
