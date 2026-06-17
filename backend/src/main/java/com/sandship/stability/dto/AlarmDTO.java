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
    private String alarmMessage;
    private String parameterName;
    private BigDecimal parameterValue;
    private BigDecimal thresholdValue;
    private Boolean isAcknowledged;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime createdAt;
}
