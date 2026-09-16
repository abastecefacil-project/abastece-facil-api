package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.model.Car;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarRepository extends JpaRepository<Car, Long> {

    Optional<Car> findByLicensePlate(String licensePlate);

    boolean existsCarByLicensePlate(String licensePlate);

    @Query(value = "SELECT * FROM cars c WHERE " +
            "(:search IS NULL OR " +
            "LOWER(c.model) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.license_plate) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
            "(:active IS NULL OR c.active = :active) " +
            "ORDER BY c.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM cars c WHERE " +
                    "(:search IS NULL OR " +
                    "LOWER(c.model) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(c.license_plate) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
                    "(:active IS NULL OR c.active = :active)",
            nativeQuery = true)
    Page<Car> findByFilters(@Param("search") String search,
                            @Param("active") Boolean active,
                            Pageable pageable);
}