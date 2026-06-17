package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoadingOptimizationRequest {

    private UUID shipId;
    private BigDecimal grainWeight;
    private BigDecimal saltWeight;
    private BigDecimal minGmRequired;
    private BigDecimal maxTrimAngle;
    private Boolean prioritizeGrain;
    private Boolean useHeuristic;
    private Boolean refineWithMip;
}
