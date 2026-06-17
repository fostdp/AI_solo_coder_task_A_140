package com.sandship.stability.mqtt_receiver;

import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.repository.SensorDataRepository;
import com.sandship.stability.repository.ShipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class SensorDataValidator {

    @Autowired
    private ShipRepository shipRepository;

    public ValidationResult validate(UUID shipId, SensorData sensorData) {
        List<String> errors = new ArrayList<>();

        Optional<Ship> shipOpt = shipRepository.findById(shipId);
        if (shipOpt.isEmpty()) {
            errors.add("船舶不存在: " + shipId);
            return new ValidationResult(false, errors);
        }

        Ship ship = shipOpt.get();

        if (sensorData.getDraftMean() != null) {
            if (sensorData.getDraftMean().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("吃水深度必须为正");
            }
            if (sensorData.getDraftMean().compareTo(ship.getDepthMolded()) > 0) {
                errors.add("吃水深度超过型深: " + sensorData.getDraftMean() + " > " + ship.getDepthMolded());
            }
        }

        if (sensorData.getRollAngle() != null) {
            BigDecimal absRoll = sensorData.getRollAngle().abs();
            if (absRoll.compareTo(new BigDecimal("90")) > 0) {
                errors.add("横摇角度超出物理范围: |" + sensorData.getRollAngle() + "| > 90°");
            }
        }

        if (sensorData.getPitchAngle() != null) {
            BigDecimal absPitch = sensorData.getPitchAngle().abs();
            if (absPitch.compareTo(new BigDecimal("45")) > 0) {
                errors.add("纵摇角度超出物理范围: |" + sensorData.getPitchAngle() + "| > 45°");
            }
        }

        if (sensorData.getBilgeWaterLevel() != null) {
            if (sensorData.getBilgeWaterLevel().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("舱底水位不能为负");
            }
        }

        if (sensorData.getWindSpeed() != null) {
            if (sensorData.getWindSpeed().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("风速不能为负");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
