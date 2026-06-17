package com.sandship.stability.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "stability")
public class StabilityConfig {

    private BigDecimal minGmThreshold = new BigDecimal("0.3");
    private BigDecimal criticalGmThreshold = new BigDecimal("0.15");
    private BigDecimal maxRollAngle = new BigDecimal("15.0");
    private BigDecimal criticalRollAngle = new BigDecimal("25.0");
    private BigDecimal maxBilgeWater = new BigDecimal("0.5");
    private BigDecimal criticalBilgeWater = new BigDecimal("1.0");
    private long calculationInterval = 60000;
    private double curveStep = 1.0;
    private double curveMaxAngle = 60.0;
}
