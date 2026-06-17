package com.sandship.stability.repository;

import com.sandship.stability.entity.CargoHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CargoHoldRepository extends JpaRepository<CargoHold, UUID> {

    List<CargoHold> findByShipIdOrderByHoldNumber(UUID shipId);

    List<CargoHold> findByShipId(UUID shipId);

    @Query("SELECT ch FROM CargoHold ch JOIN FETCH ch.ship WHERE ch.shipId = :shipId ORDER BY ch.holdNumber")
    List<CargoHold> findByShipIdWithShip(UUID shipId);
}
