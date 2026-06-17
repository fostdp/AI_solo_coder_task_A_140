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
public class LoadingOptimizationResultDTO {

    private UUID id;
    private UUID shipId;
    private String shipName;
    private LocalDateTime optimizationTime;
    private BigDecimal totalCargoWeight;
    private BigDecimal totalCargoVolume;
    private BigDecimal effectivePayload;
    private BigDecimal minGmRequired;
    private BigDecimal resultingGm;
    private BigDecimal grainWeight;
    private BigDecimal saltWeight;
    private String status;
    private List<Map<String, Object>> solution;
    private List<Map<String, Object>> holdAllocations;
    private BigDecimal objectiveValue;
    private LocalDateTime createdAt;
}
