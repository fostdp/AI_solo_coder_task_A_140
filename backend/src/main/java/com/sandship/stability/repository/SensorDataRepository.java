package com.sandship.stability.repository;

import com.sandship.stability.entity.SensorData;
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
public interface SensorDataRepository extends JpaRepository<SensorData, UUID> {

    Page<SensorData> findByShipIdOrderByTimestampDesc(UUID shipId, Pageable pageable);

    List<SensorData> findByShipIdAndTimestampBetweenOrderByTimestampDesc(
            UUID shipId, LocalDateTime startTime, LocalDateTime endTime);

    Optional<SensorData> findTopByShipIdOrderByTimestampDesc(UUID shipId);

    @Query("SELECT sd FROM SensorData sd WHERE sd.shipId = :shipId " +
           "AND sd.timestamp >= :startTime ORDER BY sd.timestamp DESC")
    List<SensorData> findRecentData(@Param("shipId") UUID shipId,
                                    @Param("startTime") LocalDateTime startTime);

    @Query(value = "SELECT * FROM sensor_data WHERE ship_id = :shipId " +
                   "ORDER BY timestamp DESC LIMIT :limit", nativeQuery = true)
    List<SensorData> findLatestN(@Param("shipId") UUID shipId, @Param("limit") int limit);
}
