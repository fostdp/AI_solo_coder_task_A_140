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
@Table(name = "alarms", indexes = {
    @Index(name = "idx_alarms_ship_time", columnList = "ship_id, alarm_time DESC"),
    @Index(name = "idx_alarms_unack", columnList = "ship_id, is_acknowledged")
})
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "sensor_data_id", columnDefinition = "uuid")
    private UUID sensorDataId;

    @Column(name = "stability_result_id", columnDefinition = "uuid")
    private UUID stabilityResultId;

    @Column(name = "alarm_time", nullable = false)
    private LocalDateTime alarmTime;

    @Column(name = "alarm_type", nullable = false, length = 50)
    private String alarmType;

    @Column(name = "alarm_level", nullable = false, length = 20)
    private String alarmLevel = "WARNING";

    @Column(name = "alarm_message", nullable = false, columnDefinition = "TEXT")
    private String alarmMessage;

    @Column(name = "parameter_name", length = 50)
    private String parameterName;

    @Column(name = "parameter_value", precision = 12, scale = 4)
    private BigDecimal parameterValue;

    @Column(name = "threshold_value", precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "is_acknowledged")
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_data_id", insertable = false, updatable = false)
    private SensorData sensorData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stability_result_id", insertable = false, updatable = false)
    private StabilityResult stabilityResult;

    @PrePersist
    protected void onCreate() {
        if (alarmTime == null) {
            alarmTime = LocalDateTime.now();
        }
    }
}
