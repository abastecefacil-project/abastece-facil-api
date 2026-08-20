package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.model.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

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
