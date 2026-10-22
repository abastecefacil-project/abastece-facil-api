package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.dto.dashboard.ContagemAgrupada;
import com.github.api_abastecefacil.model.Incident;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /** Intervalo fechado nas duas pontas, como o BETWEEN do SQL. */
    long countByOccurrenceDateBetween(LocalDate inicio, LocalDate fim);

    /**
     * Ocorrências por mês ({@code YYYY-MM}) no intervalo fechado. Meses sem ocorrência não
     * aparecem: quem preenche os zeros é o service.
     */
    @Query(value = """
            SELECT to_char(i.occurrence_date, 'YYYY-MM') AS chave, COUNT(*) AS total
              FROM incidents i
             WHERE i.occurrence_date BETWEEN :inicio AND :fim
             GROUP BY 1
            """, nativeQuery = true)
    List<ContagemAgrupada> contarPorMes(@Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim);

    /**
     * Placas com mais ocorrências no intervalo fechado, agrupadas pela placa normalizada
     * (sem espaços nas pontas, em maiúsculas). Empate desempata pela placa, para a ordem
     * ser estável entre chamadas.
     */
    @Query(value = """
            SELECT UPPER(TRIM(i.car_plate)) AS chave, COUNT(*) AS total
              FROM incidents i
             WHERE i.occurrence_date BETWEEN :inicio AND :fim
             GROUP BY 1
             ORDER BY total DESC, chave ASC
             LIMIT :limite
            """, nativeQuery = true)
    List<ContagemAgrupada> rankingPlacas(@Param("inicio") LocalDate inicio,
                                         @Param("fim") LocalDate fim,
                                         @Param("limite") int limite);

    List<Incident> findAllByOrderByCreatedAtDesc(Limit limite);

    @Query(value = """
            SELECT * FROM incidents i
            WHERE (:carPlate IS NULL OR i.car_plate = :carPlate)
              AND (:title IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :title, '%')))
              AND (:userName IS NULL OR LOWER(i.user_name) LIKE LOWER(CONCAT('%', :userName, '%')))
              AND (:occurrenceDate IS NULL OR i.occurrence_date = :occurrenceDate)
            ORDER BY i.created_at DESC
            """,
            countQuery = """
                    SELECT COUNT(*) FROM incidents i
                    WHERE (:carPlate IS NULL OR i.car_plate = :carPlate)
                      AND (:title IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :title, '%')))
                      AND (:userName IS NULL OR LOWER(i.user_name) LIKE LOWER(CONCAT('%', :userName, '%')))
                      AND (:occurrenceDate IS NULL OR i.occurrence_date = :occurrenceDate)
                    """,
            nativeQuery = true)
    Page<Incident> findByFilters(
            @Param("carPlate") String carPlate,
            @Param("title") String title,
            @Param("userName") String userName,
            @Param("occurrenceDate") LocalDate occurrenceDate,
            Pageable pageable);


}
