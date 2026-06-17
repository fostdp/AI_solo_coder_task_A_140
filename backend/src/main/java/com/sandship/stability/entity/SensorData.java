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
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sensor_data", indexes = {
    @Index(name = "idx_sensor_data_ship_time", columnList = "ship_id, timestamp DESC")
})
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "draft_forward", precision = 8, scale = 3)
    private BigDecimal draftForward;

    @Column(name = "draft_aft", precision = 8, scale = 3)
    private BigDecimal draftAft;

    @Column(name = "draft_mean", precision = 8, scale = 3)
    private BigDecimal draftMean;

    @Column(name = "roll_angle", precision = 8, scale = 3)
    private BigDecimal rollAngle;

    @Column(name = "pitch_angle", precision = 8, scale = 3)
    private BigDecimal pitchAngle;

    @Column(name = "heel_angle", precision = 8, scale = 3)
    private BigDecimal heelAngle;

    @Column(name = "bilge_water_level", precision = 8, scale = 3)
    private BigDecimal bilgeWaterLevel;

    @Column(name = "water_temperature", precision = 8, scale = 2)
    private BigDecimal waterTemperature;

    @Column(name = "wind_speed", precision = 8, scale = 2)
    private BigDecimal windSpeed;

    @Column(name = "wind_direction", precision = 8, scale = 2)
    private BigDecimal windDirection;

    @Column(name = "wave_height", precision = 8, scale = 3)
    private BigDecimal waveHeight;

    @Column(name = "mqtt_topic", length = 200)
    private String mqttTopic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
