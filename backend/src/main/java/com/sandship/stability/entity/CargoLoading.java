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
@Table(name = "cargo_loadings")
public class CargoLoading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "hold_id", nullable = false, columnDefinition = "uuid")
    private UUID holdId;

    @Column(name = "cargo_type_id", nullable = false, columnDefinition = "uuid")
    private UUID cargoTypeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal volume;

    @Column(name = "loading_time")
    private LocalDateTime loadingTime;

    @Column(name = "loading_order")
    private Integer loadingOrder;

    @Column(name = "is_optimized")
    private Boolean isOptimized = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id", insertable = false, updatable = false)
    private CargoHold cargoHold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_type_id", insertable = false, updatable = false)
    private CargoType cargoType;

    @PrePersist
    protected void onCreate() {
        if (loadingTime == null) {
            loadingTime = LocalDateTime.now();
        }
    }
}
