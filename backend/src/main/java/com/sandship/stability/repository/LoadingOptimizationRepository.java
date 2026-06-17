package com.sandship.stability.repository;

import com.sandship.stability.entity.LoadingOptimization;
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
public interface LoadingOptimizationRepository extends JpaRepository<LoadingOptimization, UUID> {

    Page<LoadingOptimization> findByShipIdOrderByOptimizationTimeDesc(UUID shipId, Pageable pageable);

    Optional<LoadingOptimization> findTopByShipIdOrderByOptimizationTimeDesc(UUID shipId);

    List<LoadingOptimization> findByShipIdAndStatusOrderByOptimizationTimeDesc(
            UUID shipId, String status);

    @Query("SELECT lo FROM LoadingOptimization lo WHERE lo.shipId = :shipId " +
           "AND lo.optimizationTime >= :startTime ORDER BY lo.optimizationTime DESC")
    List<LoadingOptimization> findRecentOptimizations(@Param("shipId") UUID shipId,
                                                      @Param("startTime") LocalDateTime startTime);
}
