package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlarmDTO {

    private UUID id;
    private UUID shipId;
    private String shipName;
    private UUID sensorDataId;
    private UUID stabilityResultId;
    private LocalDateTime alarmTime;
    private String alarmType;
    private String alarmLevel;
    private String severity;
    private String alarmMessage;
    private String description;
    private String parameterName;
    private BigDecimal parameterValue;
    private BigDecimal thresholdValue;
    private Boolean isAcknowledged;
    private Boolean acknowledged;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;
}
