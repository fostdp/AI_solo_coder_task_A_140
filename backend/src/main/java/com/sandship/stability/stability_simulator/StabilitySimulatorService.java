package com.sandship.stability.stability_simulator;

import com.sandship.stability.config.ShipParamsConfig;
import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.StabilityResultDTO;
import com.sandship.stability.entity.*;
import com.sandship.stability.events.StabilityCalculatedEvent;
import com.sandship.stability.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.FastMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class StabilitySimulatorService {

    private static final double GRAVITY = 9.81;

    @Autowired
    private StabilityResultRepository stabilityResultRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private CargoHoldRepository cargoHoldRepository;

    @Autowired
    private CargoLoadingRepository cargoLoadingRepository;

    @Autowired
    private StabilityConfig stabilityConfig;

    @Autowired
    private ShipParamsConfig shipParamsConfig;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Async
    @Transactional
    public StabilityResult calculateAndSaveStability(UUID shipId, SensorData sensorData) {
        try {
            StabilityResult result = calculateStability(shipId, sensorData);
            result = stabilityResultRepository.save(result);

            StabilityResultDTO dto = convertToDTO(result);
            eventPublisher.publishEvent(new StabilityCalculatedEvent(this, shipId, dto));

            log.info("[Stability Simulator] 稳性计算完成 - 船舶: {}, GM: {}m", shipId, result.getGmValue());
            return result;
        } catch (Exception e) {
            log.error("[Stability Simulator] 计算稳性失败 - 船舶: {}", shipId, e);
            throw new RuntimeException("稳性计算失败", e);
        }
    }

    public StabilityResult calculateStability(UUID shipId, SensorData sensorData) {
        Optional<Ship> shipOpt = shipRepository.findById(shipId);
        if (shipOpt.isEmpty()) {
            throw new IllegalArgumentException("船舶不存在: " + shipId);
        }

        Ship ship = shipOpt.get();
        List<CargoLoading> cargoLoadings = cargoLoadingRepository.findByShipIdWithDetails(shipId);
        List<CargoHold> cargoHolds = cargoHoldRepository.findByShipIdOrderByHoldNumber(shipId);

        BigDecimal[] centerOfGravity = calculateCenterOfGravity(ship, cargoLoadings, cargoHolds);
        BigDecimal displacement = calculateDisplacement(ship, sensorData, cargoLoadings);
        BigDecimal[] centerOfBuoyancy = calculateCenterOfBuoyancy(ship, sensorData);

        BigDecimal freeSurfaceCorrection = calculateFreeSurfaceCorrection(cargoHolds, displacement);

        BigDecimal gmTransverse = calculateTransverseGM(ship, centerOfGravity[2], centerOfBuoyancy[2], freeSurfaceCorrection);
        BigDecimal gmLongitudinal = calculateLongitudinalGM(ship, centerOfGravity[2], centerOfBuoyancy[2], freeSurfaceCorrection);

        BigDecimal gmUncorrected = gmTransverse.add(freeSurfaceCorrection);

        BigDecimal rollAngle = sensorData != null && sensorData.getRollAngle() != null
                ? sensorData.getRollAngle() : BigDecimal.ZERO;

        BigDecimal rightingArm = calculateRightingArm(gmTransverse, rollAngle);
        BigDecimal rightingMoment = calculateRightingMoment(displacement, rightingArm);
        BigDecimal rollPeriod = calculateRollPeriod(ship, gmTransverse, displacement);

        List<Map<String, Object>> curvePoints = generateStabilityCurve(ship, gmTransverse,
                centerOfGravity[2], displacement);

        String stabilityStatus = determineStabilityStatus(gmTransverse, rollAngle);
        String warningMessage = generateWarningMessage(gmTransverse, rollAngle,
                sensorData != null ? sensorData.getBilgeWaterLevel() : null);

        StabilityResult result = new StabilityResult();
        result.setShipId(shipId);
        result.setSensorDataId(sensorData != null ? sensorData.getId() : null);
        result.setCalculationTime(LocalDateTime.now());
        result.setDisplacementActual(displacement);
        result.setCenterGravityX(centerOfGravity[0]);
        result.setCenterGravityY(centerOfGravity[1]);
        result.setCenterGravityZ(centerOfGravity[2]);
        result.setCenterBuoyancyX(centerOfBuoyancy[0]);
        result.setCenterBuoyancyY(centerOfBuoyancy[1]);
        result.setCenterBuoyancyZ(centerOfBuoyancy[2]);
        result.setMetacentricHeightTransverse(gmTransverse);
        result.setMetacentricHeightLongitudinal(gmLongitudinal);
        result.setRightingArm(rightingArm);
        result.setRightingMoment(rightingMoment);
        result.setRollPeriod(rollPeriod);
        result.setGmValue(gmTransverse);
        result.setFreeSurfaceCorrection(freeSurfaceCorrection);
        result.setGmUncorrected(gmUncorrected);
        result.setStabilityStatus(stabilityStatus);
        result.setWarningMessage(warningMessage);
        result.setCurvePoints(curvePoints);

        return result;
    }

    private BigDecimal[] calculateCenterOfGravity(Ship ship, List<CargoLoading> cargoLoadings,
                                                  List<CargoHold> cargoHolds) {
        BigDecimal totalWeight = ship.getLightshipWeight();
        BigDecimal momentX = ship.getLightshipWeight().multiply(BigDecimal.ZERO);
        BigDecimal momentY = ship.getLightshipWeight().multiply(BigDecimal.ZERO);
        BigDecimal momentZ = ship.getLightshipWeight().multiply(new BigDecimal("0.8"));

        for (CargoHold hold : cargoHolds) {
            if (Boolean.TRUE.equals(hold.getIsTank()) && hold.getTankFullness() != null
                    && hold.getLiquidDensity() != null && hold.getCapacityCubic() != null) {
                double fullness = hold.getTankFullness().doubleValue();
                double liquidWeight = hold.getCapacityCubic().doubleValue()
                        * hold.getLiquidDensity().doubleValue() * fullness;
                if (liquidWeight > 0.01) {
                    BigDecimal lw = BigDecimal.valueOf(liquidWeight);
                    totalWeight = totalWeight.add(lw);
                    momentX = momentX.add(lw.multiply(hold.getCenterGravityX()));
                    momentY = momentY.add(lw.multiply(hold.getCenterGravityY()));
                    momentZ = momentZ.add(lw.multiply(hold.getCenterGravityZ()));
                }
            }
        }

        Map<UUID, CargoHold> holdMap = new HashMap<>();
        for (CargoHold hold : cargoHolds) {
            holdMap.put(hold.getId(), hold);
        }

        for (CargoLoading loading : cargoLoadings) {
            CargoHold hold = holdMap.get(loading.getHoldId());
            if (hold != null) {
                BigDecimal weight = loading.getWeight();
                totalWeight = totalWeight.add(weight);
                momentX = momentX.add(weight.multiply(hold.getCenterGravityX()));
                momentY = momentY.add(weight.multiply(hold.getCenterGravityY()));
                momentZ = momentZ.add(weight.multiply(hold.getCenterGravityZ()));
            }
        }

        BigDecimal cgX = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? momentX.divide(totalWeight, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal cgY = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? momentY.divide(totalWeight, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal cgZ = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? momentZ.divide(totalWeight, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new BigDecimal[]{cgX, cgY, cgZ};
    }

    private BigDecimal calculateDisplacement(Ship ship, SensorData sensorData,
                                             List<CargoLoading> cargoLoadings) {
        BigDecimal cargoWeight = cargoLoadings.stream()
                .map(CargoLoading::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sensorData != null && sensorData.getDraftMean() != null) {
            BigDecimal draft = sensorData.getDraftMean();
            BigDecimal length = ship.getLengthOverall();
            BigDecimal breadth = ship.getBreadthMolded();
            double blockCoefficient = shipParamsConfig.getHull().getDefaultBlockCoefficient().doubleValue();
            double seawaterDensity = shipParamsConfig.getHull().getSeawaterDensity().doubleValue();

            BigDecimal displacement = length.multiply(breadth).multiply(draft)
                    .multiply(BigDecimal.valueOf(blockCoefficient))
                    .multiply(BigDecimal.valueOf(seawaterDensity));

            return displacement.setScale(2, RoundingMode.HALF_UP);
        }

        return ship.getLightshipWeight().add(cargoWeight);
    }

    private BigDecimal[] calculateCenterOfBuoyancy(Ship ship, SensorData sensorData) {
        BigDecimal draft = sensorData != null && sensorData.getDraftMean() != null
                ? sensorData.getDraftMean() : ship.getDesignDraft();

        BigDecimal cbX = BigDecimal.ZERO;
        BigDecimal cbY = BigDecimal.ZERO;
        double kbCoefficient = shipParamsConfig.getHull().getBuoyancyCenterCoefficient().doubleValue();
        BigDecimal cbZ = draft.multiply(BigDecimal.valueOf(kbCoefficient));

        if (sensorData != null && sensorData.getDraftForward() != null
                && sensorData.getDraftAft() != null) {
            BigDecimal trim = sensorData.getDraftForward().subtract(sensorData.getDraftAft());
            BigDecimal lcg = trim.divide(ship.getLengthOverall(), 3, RoundingMode.HALF_UP)
                    .multiply(ship.getLengthOverall().multiply(new BigDecimal("0.5")));
            cbX = lcg;
        }

        return new BigDecimal[]{cbX, cbY, cbZ};
    }

    private BigDecimal calculateTransverseGM(Ship ship, BigDecimal cgZ, BigDecimal cbZ, BigDecimal fsc) {
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double displacement = ship.getDisplacement().doubleValue();
        double seawaterDensity = shipParamsConfig.getHull().getSeawaterDensity().doubleValue();

        double it = (length * FastMath.pow(breadth, 3)) / 12.0;
        double kb = cbZ.doubleValue();
        double bmt = it / displacement * seawaterDensity;
        double km = kb + bmt;
        double gm = km - cgZ.doubleValue() - fsc.doubleValue();

        return BigDecimal.valueOf(gm).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLongitudinalGM(Ship ship, BigDecimal cgZ, BigDecimal cbZ, BigDecimal fsc) {
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double displacement = ship.getDisplacement().doubleValue();
        double seawaterDensity = shipParamsConfig.getHull().getSeawaterDensity().doubleValue();

        double il = (breadth * FastMath.pow(length, 3)) / 12.0;
        double kb = cbZ.doubleValue();
        double bml = il / displacement * seawaterDensity;
        double kml = kb + bml;
        double gml = kml - cgZ.doubleValue() - fsc.doubleValue();

        return BigDecimal.valueOf(gml).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFreeSurfaceCorrection(List<CargoHold> cargoHolds, BigDecimal displacement) {
        double totalFsc = 0.0;
        double disp = displacement.doubleValue();

        for (CargoHold hold : cargoHolds) {
            if (Boolean.TRUE.equals(hold.getIsTank())
                    && hold.getTankLength() != null
                    && hold.getTankBreadth() != null
                    && hold.getLiquidDensity() != null
                    && hold.getTankFullness() != null) {

                double l = hold.getTankLength().doubleValue();
                double b = hold.getTankBreadth().doubleValue();
                double rho = hold.getLiquidDensity().doubleValue();
                double fullness = hold.getTankFullness().doubleValue();

                if (fullness > 0.01 && fullness < 0.99) {
                    double it = (l * FastMath.pow(b, 3)) / 12.0;
                    double correctionFactor = 1.0 - FastMath.abs(fullness - 0.5) * 0.4;
                    double fsc = (rho * it * correctionFactor) / disp;
                    totalFsc += fsc;
                }
            }
        }
        return BigDecimal.valueOf(totalFsc).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRightingArm(BigDecimal gm, BigDecimal rollAngle) {
        double angleRad = FastMath.toRadians(rollAngle.doubleValue());
        double rightingArm = gm.doubleValue() * FastMath.sin(angleRad);
        return BigDecimal.valueOf(rightingArm).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRightingMoment(BigDecimal displacement, BigDecimal rightingArm) {
        double moment = displacement.doubleValue() * GRAVITY * rightingArm.doubleValue();
        return BigDecimal.valueOf(moment).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRollPeriod(Ship ship, BigDecimal gm, BigDecimal displacement) {
        if (gm.doubleValue() <= 0) return BigDecimal.ZERO;

        double breadth = ship.getBreadthMolded().doubleValue();
        double kFactor = shipParamsConfig.getHull().getRollRadiusCoefficient().doubleValue();
        double massRadiusOfGyration = breadth * kFactor;
        double period = 2 * Math.PI * massRadiusOfGyration /
                FastMath.sqrt(GRAVITY * gm.doubleValue());

        return BigDecimal.valueOf(period).setScale(3, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> generateStabilityCurve(Ship ship, BigDecimal gm,
                                                              double cgZ, BigDecimal displacement) {
        List<Map<String, Object>> curvePoints = new ArrayList<>();
        double step = stabilityConfig.getCurveStep();
        double maxAngle = stabilityConfig.getCurveMaxAngle();

        for (double angle = 0; angle <= maxAngle; angle += step) {
            double angleRad = FastMath.toRadians(angle);
            double rightingArm = gm.doubleValue() * FastMath.sin(angleRad);

            if (angle > 30) {
                double reductionFactor = 1 - (angle - 30) / 60;
                rightingArm *= Math.max(reductionFactor, 0.3);
            }

            double rightingMoment = displacement.doubleValue() * GRAVITY * rightingArm;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("angle", round(angle, 1));
            point.put("rightingArm", round(rightingArm, 4));
            point.put("rightingMoment", round(rightingMoment, 2));
            curvePoints.add(point);
        }
        return curvePoints;
    }

    private String determineStabilityStatus(BigDecimal gm, BigDecimal rollAngle) {
        BigDecimal minGm = stabilityConfig.getMinGmThreshold();
        BigDecimal maxRoll = stabilityConfig.getMaxRollAngle();

        if (gm.compareTo(new BigDecimal("0.15")) < 0) return "CRITICAL";
        if (gm.compareTo(minGm) < 0) return "WARNING";
        if (rollAngle.abs().compareTo(maxRoll) > 0) return "WARNING";
        return "NORMAL";
    }

    private String generateWarningMessage(BigDecimal gm, BigDecimal rollAngle, BigDecimal bilgeWater) {
        List<String> warnings = new ArrayList<>();
        BigDecimal minGm = stabilityConfig.getMinGmThreshold();
        BigDecimal maxRoll = stabilityConfig.getMaxRollAngle();
        BigDecimal maxBilge = stabilityConfig.getMaxBilgeWater();

        if (gm.compareTo(minGm) < 0) {
            warnings.add(String.format("GM值(%.3fm)低于安全阈值(%.3fm)", gm.doubleValue(), minGm.doubleValue()));
        }
        if (rollAngle != null && rollAngle.abs().compareTo(maxRoll) > 0) {
            warnings.add(String.format("横摇角(%.2f°)超过安全阈值(%.2f°)",
                    rollAngle.abs().doubleValue(), maxRoll.doubleValue()));
        }
        if (bilgeWater != null && bilgeWater.compareTo(maxBilge) > 0) {
            warnings.add(String.format("舱底水位(%.3fm)超过安全阈值(%.3fm)",
                    bilgeWater.doubleValue(), maxBilge.doubleValue()));
        }
        return warnings.isEmpty() ? null : String.join("; ", warnings);
    }

    private double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    public StabilityResultDTO convertToDTO(StabilityResult result) {
        StabilityResultDTO dto = new StabilityResultDTO();
        dto.setId(result.getId());
        dto.setShipId(result.getShipId());
        dto.setSensorDataId(result.getSensorDataId());
        dto.setCalculationTime(result.getCalculationTime());
        dto.setDisplacementActual(result.getDisplacementActual());
        dto.setCenterGravityX(result.getCenterGravityX());
        dto.setCenterGravityY(result.getCenterGravityY());
        dto.setCenterGravityZ(result.getCenterGravityZ());
        dto.setCenterBuoyancyX(result.getCenterBuoyancyX());
        dto.setCenterBuoyancyY(result.getCenterBuoyancyY());
        dto.setCenterBuoyancyZ(result.getCenterBuoyancyZ());
        dto.setMetacentricHeightTransverse(result.getMetacentricHeightTransverse());
        dto.setMetacentricHeightLongitudinal(result.getMetacentricHeightLongitudinal());
        dto.setRightingArm(result.getRightingArm());
        dto.setRightingMoment(result.getRightingMoment());
        dto.setRollPeriod(result.getRollPeriod());
        dto.setGmValue(result.getGmValue());
        dto.setFreeSurfaceCorrection(result.getFreeSurfaceCorrection());
        dto.setGmUncorrected(result.getGmUncorrected());
        dto.setStabilityStatus(result.getStabilityStatus());
        dto.setWarningMessage(result.getWarningMessage());
        dto.setCurvePoints(result.getCurvePoints());
        dto.setCreatedAt(result.getCreatedAt());

        shipRepository.findById(result.getShipId()).ifPresent(ship ->
                dto.setShipName(ship.getName()));

        return dto;
    }
}
