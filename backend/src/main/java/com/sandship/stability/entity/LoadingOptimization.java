package com.sandship.stability.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loading_optimizations")
public class LoadingOptimization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "optimization_time", nullable = false)
    private LocalDateTime optimizationTime;

    @Column(name = "total_cargo_weight", precision = 12, scale = 2)
    private BigDecimal totalCargoWeight;

    @Column(name = "total_cargo_volume", precision = 12, scale = 2)
    private BigDecimal totalCargoVolume;

    @Column(name = "effective_payload", precision = 12, scale = 2)
    private BigDecimal effectivePayload;

    @Column(name = "min_gm_required", nullable = false, precision = 8, scale = 4)
    private BigDecimal minGmRequired = new BigDecimal("0.3");

    @Column(name = "resulting_gm", precision = 8, scale = 4)
    private BigDecimal resultingGm;

    @Column(name = "grain_weight", precision = 12, scale = 2)
    private BigDecimal grainWeight;

    @Column(name = "salt_weight", precision = 12, scale = 2)
    private BigDecimal saltWeight;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> solution;

    @Column(name = "objective_value", precision = 12, scale = 2)
    private BigDecimal objectiveValue;

    @Column(name = "solve_time_ms", precision = 12, scale = 0)
    private BigDecimal solveTimeMs;

    @Column(name = "algorithm_used", length = 50)
    private String algorithmUsed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @PrePersist
    protected void onCreate() {
        if (optimizationTime == null) {
            optimizationTime = LocalDateTime.now();
        }
    }
}
