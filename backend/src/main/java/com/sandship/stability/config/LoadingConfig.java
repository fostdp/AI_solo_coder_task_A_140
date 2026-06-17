package com.sandship.stability.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "loading")
public class LoadingConfig {

    private BigDecimal defaultMinGm = new BigDecimal("0.3");
    private long optimizationTimeout = 30000;
    private int maxIterations = 1000;
}
