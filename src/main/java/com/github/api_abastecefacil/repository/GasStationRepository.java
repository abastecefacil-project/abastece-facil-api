package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.dto.dashboard.ContagemCidade;
import com.github.api_abastecefacil.model.GasStation;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import java.time.LocalDateTime;

import java.util.List;

public interface GasStationRepository extends JpaRepository<GasStation, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO avaliacoes_neo (posto_id, usuario_id, passou_neo)
            VALUES (:postoId, :usuarioId, :passouNeo)
            ON CONFLICT (posto_id, usuario_id)
            DO UPDATE SET passou_neo = EXCLUDED.passou_neo, atualizado_em = CURRENT_TIMESTAMP
            WHERE avaliacoes_neo.passou_neo IS DISTINCT FROM EXCLUDED.passou_neo
            """, nativeQuery = true)
    int salvarAvaliacaoNeo(@Param("postoId") Long postoId,
                           @Param("usuarioId") Long usuarioId,
                           @Param("passouNeo") boolean passouNeo);

    @Modifying
    @Query(value = "DELETE FROM avaliacoes_neo WHERE posto_id = :postoId AND usuario_id = :usuarioId", nativeQuery = true)
    void removerAvaliacaoNeo(@Param("postoId") Long postoId, @Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT passou_neo FROM avaliacoes_neo WHERE posto_id = :postoId AND usuario_id = :usuarioId", nativeQuery = true)
    Optional<Boolean> buscarAvaliacaoNeo(@Param("postoId") Long postoId,
                                       @Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT COUNT(*) FROM avaliacoes_neo WHERE posto_id = :postoId AND passou_neo = :passouNeo", nativeQuery = true)
    long contarAvaliacoesNeo(@Param("postoId") Long postoId,
                            @Param("passouNeo") boolean passouNeo);

    @Query(value = "SELECT MAX(atualizado_em) FROM avaliacoes_neo WHERE posto_id = :postoId", nativeQuery = true)
    Optional<LocalDateTime> buscarUltimoRelatoNeo(@Param("postoId") Long postoId);

    boolean existsGasStationById(Long id);

    boolean existsByCnpj(String cnpj);

    @Query(value = "SELECT * FROM gas_stations WHERE " +
            "(:search IS NULL OR " +
            "LOWER(name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(fantasy_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "cnpj LIKE CONCAT('%', :search, '%')) AND " +
            "(:active IS NULL OR is_active = :active) " +
            "ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM gas_stations WHERE " +
                    "(:search IS NULL OR " +
                    "LOWER(name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(fantasy_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "cnpj LIKE CONCAT('%', :search, '%')) AND " +
                    "(:active IS NULL OR is_active = :active)",
            nativeQuery = true)
    Page<GasStation> findByFilters(@Param("search") String search,
                                   @Param("active") Boolean active,
                                   Pageable pageable);

    long countByIsActive(Boolean isActive);

    /**
     * Cidades distintas entre os postos ativos, para o resumo da Home.
     *
     * <p>A chave é o par cidade + UF, porque o mesmo nome existe em estados diferentes. Caixa
     * e espaços nas pontas são neutralizados, já que o cadastro manual digita o texto livre;
     * <b>acento não é</b>: o banco não tem a extensão {@code unaccent}, e "Sao Jose" e
     * "São José" contariam como duas cidades.
     */
    @Query(value = """
            SELECT COUNT(DISTINCT (LOWER(TRIM(city)), UPPER(TRIM(state))))
              FROM gas_stations
             WHERE is_active = true
            """, nativeQuery = true)
    long countCidadesAtendidas();

    /**
     * Cidades com mais postos ativos, agrupadas pela mesma chave de
     * {@link #countCidadesAtendidas} — as duas precisam continuar iguais, senão o ranking e o
     * total de cidades deixam de falar das mesmas cidades. {@code MIN(TRIM(city))} escolhe um
     * rótulo entre as grafias que caíram no mesmo grupo.
     *
     * <p>O {@code @Param} vem qualificado de propósito: o import deste arquivo é o
     * {@code feign.Param}, que o Spring Data ignora.
     */
    @Query(value = """
            SELECT MIN(TRIM(city)) AS cidade, UPPER(TRIM(state)) AS uf, COUNT(*) AS total
              FROM gas_stations
             WHERE is_active = true
             GROUP BY LOWER(TRIM(city)), UPPER(TRIM(state))
             ORDER BY total DESC, cidade ASC
             LIMIT :limite
            """, nativeQuery = true)
    List<ContagemCidade> rankingCidades(
            @org.springframework.data.repository.query.Param("limite") int limite);
}
