package com.sandship.stability.repository;

import com.sandship.stability.entity.CargoLoading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CargoLoadingRepository extends JpaRepository<CargoLoading, UUID> {

    List<CargoLoading> findByShipIdOrderByLoadingTimeDesc(UUID shipId);

    List<CargoLoading> findByShipIdAndHoldIdOrderByLoadingTimeDesc(UUID shipId, UUID holdId);

    @Query("SELECT cl FROM CargoLoading cl WHERE cl.shipId = :shipId AND cl.isOptimized = :isOptimized")
    List<CargoLoading> findByShipIdAndOptimized(@Param("shipId") UUID shipId,
                                                @Param("isOptimized") Boolean isOptimized);

    @Query("SELECT COALESCE(SUM(cl.weight), 0) FROM CargoLoading cl WHERE cl.shipId = :shipId")
    java.math.BigDecimal calculateTotalWeightByShipId(@Param("shipId") UUID shipId);

    @Query("SELECT COALESCE(SUM(cl.volume), 0) FROM CargoLoading cl WHERE cl.shipId = :shipId")
    java.math.BigDecimal calculateTotalVolumeByShipId(@Param("shipId") UUID shipId);

    @Query("SELECT cl FROM CargoLoading cl JOIN FETCH cl.cargoHold JOIN FETCH cl.cargoType " +
           "WHERE cl.shipId = :shipId ORDER BY cl.cargoHold.holdNumber")
    List<CargoLoading> findByShipIdWithDetails(@Param("shipId") UUID shipId);
}
