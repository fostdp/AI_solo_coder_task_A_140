package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataDTO {

    private UUID id;
    private UUID shipId;
    private LocalDateTime timestamp;
    private BigDecimal draftForward;
    private BigDecimal draftAft;
    private BigDecimal draftMean;
    private BigDecimal rollAngle;
    private BigDecimal pitchAngle;
    private BigDecimal heelAngle;
    private BigDecimal bilgeWaterLevel;
    private BigDecimal waterTemperature;
    private BigDecimal windSpeed;
    private BigDecimal windDirection;
    private BigDecimal waveHeight;
    private String mqttTopic;
    private Map<String, Object> rawPayload;
    private Map<String, Object> cargoDistribution;
    private LocalDateTime createdAt;
}
