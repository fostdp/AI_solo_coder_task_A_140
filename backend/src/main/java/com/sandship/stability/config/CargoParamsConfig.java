package com.sandship.stability.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Configuration
@PropertySource(value = "classpath:cargo-params.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "cargo")
public class CargoParamsConfig {

    private Map<String, CargoSpec> types;
    private OptimizationParams optimization;

    @Data
    public static class CargoSpec {
        private String cargoCode;
        private String cargoName;
        private BigDecimal density;
        private BigDecimal unitWeight;
        private String colorHex;
        private BigDecimal priorityWeight;
        private BigDecimal unitValue;
        private BigDecimal stowageFactor;
    }

    @Data
    public static class OptimizationParams {
        private BigDecimal defaultGrainPriority;
        private BigDecimal defaultSaltPriority;
        private BigDecimal minGmRequired;
        private BigDecimal maxRollAngle;
        private BigDecimal maxTrim;
    }
}
