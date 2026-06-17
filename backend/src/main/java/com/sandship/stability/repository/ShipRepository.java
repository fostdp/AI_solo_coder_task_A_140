package com.sandship.stability.repository;

import com.sandship.stability.entity.Ship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {

    Optional<Ship> findByName(String name);

    List<Ship> findByShipType(String shipType);

    @Query("SELECT s FROM Ship s WHERE s.name LIKE %:keyword%")
    List<Ship> searchByName(String keyword);
}
