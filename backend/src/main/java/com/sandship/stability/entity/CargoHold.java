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
@Table(name = "cargo_holds")
public class CargoHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "hold_number", nullable = false)
    private Integer holdNumber;

    @Column(name = "hold_name", nullable = false, length = 50)
    private String holdName;

    @Column(name = "capacity_cubic", nullable = false, precision = 12, scale = 2)
    private BigDecimal capacityCubic;

    @Column(name = "max_weight", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxWeight;

    @Column(name = "center_gravity_x", nullable = false, precision = 10, scale = 2)
    private BigDecimal centerGravityX;

    @Column(name = "center_gravity_y", nullable = false, precision = 10, scale = 2)
    private BigDecimal centerGravityY;

    @Column(name = "center_gravity_z", nullable = false, precision = 10, scale = 2)
    private BigDecimal centerGravityZ;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;
}
