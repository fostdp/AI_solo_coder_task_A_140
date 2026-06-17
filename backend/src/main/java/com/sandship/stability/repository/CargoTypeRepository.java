package com.sandship.stability.repository;

import com.sandship.stability.entity.CargoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CargoTypeRepository extends JpaRepository<CargoType, UUID> {

    Optional<CargoType> findByCargoCode(String cargoCode);

    boolean existsByCargoCode(String cargoCode);
}
