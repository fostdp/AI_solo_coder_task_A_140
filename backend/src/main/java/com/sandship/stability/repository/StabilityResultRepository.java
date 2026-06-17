package com.sandship.stability.repository;

import com.sandship.stability.entity.StabilityResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StabilityResultRepository extends JpaRepository<StabilityResult, UUID> {

    Page<StabilityResult> findByShipIdOrderByCalculationTimeDesc(UUID shipId, Pageable pageable);

    Optional<StabilityResult> findTopByShipIdOrderByCalculationTimeDesc(UUID shipId);

    List<StabilityResult> findByShipIdAndCalculationTimeBetweenOrderByCalculationTimeDesc(
            UUID shipId, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT sr FROM StabilityResult sr WHERE sr.shipId = :shipId " +
           "AND sr.stabilityStatus <> 'NORMAL' ORDER BY sr.calculationTime DESC")
    List<StabilityResult> findWarningsByShipId(@Param("shipId") UUID shipId);

    @Query(value = "SELECT * FROM stability_results WHERE ship_id = :shipId " +
                   "ORDER BY calculation_time DESC LIMIT :limit", nativeQuery = true)
    List<StabilityResult> findLatestN(@Param("shipId") UUID shipId, @Param("limit") int limit);

    @Query("SELECT AVG(sr.gmValue) FROM StabilityResult sr WHERE sr.shipId = :shipId " +
           "AND sr.calculationTime >= :startTime")
    java.math.BigDecimal calculateAverageGm(@Param("shipId") UUID shipId,
                                            @Param("startTime") LocalDateTime startTime);
}
