package com.github.api_abastecefacil.repository;

import com.github.api_abastecefacil.model.GasStation;
import feign.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GasStationRepository extends JpaRepository<GasStation, Long> {
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
}
