package com.sandship.stability.repository;

import com.sandship.stability.entity.Alarm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlarmRepository extends JpaRepository<Alarm, UUID> {

    Page<Alarm> findByShipIdOrderByAlarmTimeDesc(UUID shipId, Pageable pageable);

    List<Alarm> findByShipIdAndIsAcknowledgedFalseOrderByAlarmTimeDesc(UUID shipId);

    @Query("SELECT COUNT(a) FROM Alarm a WHERE a.shipId = :shipId AND a.isAcknowledged = false")
    long countUnacknowledgedByShipId(@Param("shipId") UUID shipId);

    @Modifying
    @Transactional
    @Query("UPDATE Alarm a SET a.isAcknowledged = true, a.acknowledgedAt = :acknowledgedAt " +
           "WHERE a.shipId = :shipId AND a.isAcknowledged = false")
    int acknowledgeAllByShipId(@Param("shipId") UUID shipId,
                               @Param("acknowledgedAt") LocalDateTime acknowledgedAt);

    @Query("SELECT a FROM Alarm a WHERE a.shipId = :shipId " +
           "AND a.alarmTime >= :startTime ORDER BY a.alarmTime DESC")
    List<Alarm> findRecentAlarms(@Param("shipId") UUID shipId,
                                 @Param("startTime") LocalDateTime startTime);

    List<Alarm> findByShipIdAndAlarmLevelOrderByAlarmTimeDesc(UUID shipId, String alarmLevel);
}
