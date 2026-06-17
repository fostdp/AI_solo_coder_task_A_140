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
public class CargoLoadingDTO {

    private UUID id;
    private UUID shipId;
    private String shipName;
    private UUID holdId;
    private String holdName;
    private Integer holdNumber;
    private UUID cargoTypeId;
    private String cargoName;
    private String cargoCode;
    private String colorHex;
    private BigDecimal weight;
    private BigDecimal volume;
    private BigDecimal density;
    private LocalDateTime loadingTime;
    private Integer loadingOrder;
    private Boolean isOptimized;
    private LocalDateTime createdAt;
}
