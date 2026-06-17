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
@Table(name = "stability_results", indexes = {
    @Index(name = "idx_stability_ship_time", columnList = "ship_id, calculation_time DESC")
})
public class StabilityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "sensor_data_id", columnDefinition = "uuid")
    private UUID sensorDataId;

    @Column(name = "calculation_time", nullable = false)
    private LocalDateTime calculationTime;

    @Column(name = "displacement_actual", precision = 12, scale = 2)
    private BigDecimal displacementActual;

    @Column(name = "center_gravity_x", precision = 10, scale = 3)
    private BigDecimal centerGravityX;

    @Column(name = "center_gravity_y", precision = 10, scale = 3)
    private BigDecimal centerGravityY;

    @Column(name = "center_gravity_z", precision = 10, scale = 3)
    private BigDecimal centerGravityZ;

    @Column(name = "center_buoyancy_x", precision = 10, scale = 3)
    private BigDecimal centerBuoyancyX;

    @Column(name = "center_buoyancy_y", precision = 10, scale = 3)
    private BigDecimal centerBuoyancyY;

    @Column(name = "center_buoyancy_z", precision = 10, scale = 3)
    private BigDecimal centerBuoyancyZ;

    @Column(name = "metacentric_height_transverse", precision = 8, scale = 4)
    private BigDecimal metacentricHeightTransverse;

    @Column(name = "metacentric_height_longitudinal", precision = 8, scale = 4)
    private BigDecimal metacentricHeightLongitudinal;

    @Column(name = "righting_arm", precision = 10, scale = 4)
    private BigDecimal rightingArm;

    @Column(name = "righting_moment", precision = 12, scale = 2)
    private BigDecimal rightingMoment;

    @Column(name = "roll_period", precision = 10, scale = 3)
    private BigDecimal rollPeriod;

    @Column(name = "gm_value", precision = 8, scale = 4)
    private BigDecimal gmValue;

    @Column(name = "free_surface_correction", precision = 8, scale = 4)
    private BigDecimal freeSurfaceCorrection;

    @Column(name = "gm_uncorrected", precision = 8, scale = 4)
    private BigDecimal gmUncorrected;

    @Column(name = "stability_status", nullable = false, length = 20)
    private String stabilityStatus = "NORMAL";

    @Column(name = "warning_message", columnDefinition = "TEXT")
    private String warningMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "curve_points", columnDefinition = "jsonb")
    private List<Map<String, Object>> curvePoints;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_data_id", insertable = false, updatable = false)
    private SensorData sensorData;

    @PrePersist
    protected void onCreate() {
        if (calculationTime == null) {
            calculationTime = LocalDateTime.now();
        }
    }
}
