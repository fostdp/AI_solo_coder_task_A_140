package com.sandship.stability.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Configuration
@PropertySource(value = "classpath:ship-params.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "ship")
public class ShipParamsConfig {

    private HullParams hull;
    private Map<String, ShipSpectrum> spectrum;
    private CargoHoldsParams cargoHolds;
    private TanksParams tanks;

    @Data
    public static class HullParams {
        private BlockCoefficients blockCoefficients;
        private BigDecimal defaultBlockCoefficient;
        private BigDecimal rollRadiusCoefficient;
        private BigDecimal buoyancyCenterCoefficient;
        private BigDecimal seawaterDensity;
        private BigDecimal designMetacentricHeight;
    }

    @Data
    public static class BlockCoefficients {
        private BigDecimal lightship;
        private BigDecimal halfLoaded;
        private BigDecimal fullLoaded;
    }

    @Data
    public static class ShipSpectrum {
        private BigDecimal lengthOverall;
        private BigDecimal breadthMolded;
        private BigDecimal depthMolded;
        private BigDecimal designDraft;
        private BigDecimal displacement;
        private BigDecimal lightshipWeight;
        private BigDecimal deadweightTons;
    }

    @Data
    public static class CargoHoldsParams {
        private int standardCount;
        private Map<String, BigDecimal> capacityPerHold;
    }

    @Data
    public static class TanksParams {
        private TankSpec freshWater;
        private TankSpec ballast;
    }

    @Data
    public static class TankSpec {
        private int count;
        private BigDecimal defaultLength;
        private BigDecimal defaultBreadth;
        private BigDecimal defaultDensity;
    }
}
