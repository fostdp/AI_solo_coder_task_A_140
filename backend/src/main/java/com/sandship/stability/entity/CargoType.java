package com.sandship.stability.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cargo_types")
public class CargoType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "cargo_code", nullable = false, unique = true, length = 20)
    private String cargoCode;

    @Column(name = "cargo_name", nullable = false, length = 50)
    private String cargoName;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal density;

    @Column(name = "unit_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitWeight;

    @Column(name = "color_hex", length = 7)
    private String colorHex = "#FFD700";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
