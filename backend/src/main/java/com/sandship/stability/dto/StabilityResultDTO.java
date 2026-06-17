package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StabilityResultDTO {

    private UUID id;
    private UUID shipId;
    private String shipName;
    private UUID sensorDataId;
    private LocalDateTime calculationTime;
    private BigDecimal displacementActual;
    private BigDecimal centerGravityX;
    private BigDecimal centerGravityY;
    private BigDecimal centerGravityZ;
    private BigDecimal centerBuoyancyX;
    private BigDecimal centerBuoyancyY;
    private BigDecimal centerBuoyancyZ;
    private BigDecimal metacentricHeightTransverse;
    private BigDecimal metacentricHeightLongitudinal;
    private BigDecimal rightingArm;
    private BigDecimal rightingMoment;
    private BigDecimal rollPeriod;
    private BigDecimal gmValue;
    private BigDecimal freeSurfaceCorrection;
    private BigDecimal gmUncorrected;
    private String stabilityStatus;
    private String warningMessage;
    private List<Map<String, Object>> curvePoints;
    private List<Map<String, Object>> cargoDetails;
    private LocalDateTime createdAt;
}
