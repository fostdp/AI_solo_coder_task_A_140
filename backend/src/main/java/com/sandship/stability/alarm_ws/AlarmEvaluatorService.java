package com.sandship.stability.alarm_ws;

import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.AlarmDTO;
import com.sandship.stability.dto.StabilityResultDTO;
import com.sandship.stability.entity.Alarm;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.StabilityResult;
import com.sandship.stability.events.AlarmTriggeredEvent;
import com.sandship.stability.repository.AlarmRepository;
import com.sandship.stability.repository.SensorDataRepository;
import com.sandship.stability.repository.StabilityResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AlarmEvaluatorService {

    private static final long ALARM_COOLDOWN_MINUTES = 5;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private StabilityResultRepository stabilityResultRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private StabilityConfig stabilityConfig;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AlarmWebSocketNotifier webSocketNotifier;

    public List<AlarmDTO> checkAndTriggerAlarms(UUID shipId, SensorData sensorData) {
        List<AlarmDTO> triggeredAlarms = new ArrayList<>();

        Optional<StabilityResult> latestResult = stabilityResultRepository
                .findTopByShipIdOrderByCalculationTimeDesc(shipId);

        if (latestResult.isEmpty()) {
            log.warn("[Alarm Evaluator] 无稳性结果，跳过告警检测 - 船舶: {}", shipId);
            return triggeredAlarms;
        }

        StabilityResult stability = latestResult.get();
        BigDecimal gm = stability.getGmValue();
        BigDecimal rollAngle = sensorData != null && sensorData.getRollAngle() != null
                ? sensorData.getRollAngle() : BigDecimal.ZERO;
        BigDecimal bilgeWater = sensorData != null ? sensorData.getBilgeWaterLevel() : null;

        List<Alarm> alarmsToTrigger = new ArrayList<>();

        checkGmAlarm(shipId, gm, stability, alarmsToTrigger);
        checkRollAngleAlarm(shipId, rollAngle, stability, sensorData, alarmsToTrigger);
        if (bilgeWater != null) {
            checkBilgeWaterAlarm(shipId, bilgeWater, stability, sensorData, alarmsToTrigger);
        }

        for (Alarm alarm : alarmsToTrigger) {
            if (shouldTriggerAlarm(shipId, alarm.getAlarmType(), alarm.getSeverity())) {
                Alarm saved = alarmRepository.save(alarm);
                AlarmDTO dto = convertToDTO(saved);
                triggeredAlarms.add(dto);

                eventPublisher.publishEvent(new AlarmTriggeredEvent(this, shipId, dto));
                webSocketNotifier.broadcastAlarm(shipId, dto);

                log.warn("[Alarm Evaluator] 告警触发 - 船舶: {}, 类型: {}, 级别: {}, 描述: {}",
                        shipId, alarm.getAlarmType(), alarm.getSeverity(), alarm.getDescription());
            }
        }

        return triggeredAlarms;
    }

    private void checkGmAlarm(UUID shipId, BigDecimal gm, StabilityResult stability, List<Alarm> alarms) {
        BigDecimal minGm = stabilityConfig.getMinGmThreshold();
        BigDecimal criticalGm = stabilityConfig.getCriticalGmThreshold();

        if (gm.compareTo(criticalGm) < 0) {
            Alarm alarm = createAlarm(shipId, "GM_CRITICAL", "CRITICAL",
                    String.format("GM值严重不足: %.3fm < %.3fm，存在倾覆风险！", gm.doubleValue(), criticalGm.doubleValue()),
                    stability.getId(), null);
            alarms.add(alarm);
        } else if (gm.compareTo(minGm) < 0) {
            Alarm alarm = createAlarm(shipId, "GM_LOW", "WARNING",
                    String.format("GM值偏低: %.3fm < %.3fm，稳性储备不足", gm.doubleValue(), minGm.doubleValue()),
                    stability.getId(), null);
            alarms.add(alarm);
        }
    }

    private void checkRollAngleAlarm(UUID shipId, BigDecimal rollAngle, StabilityResult stability,
                                      SensorData sensorData, List<Alarm> alarms) {
        BigDecimal maxRoll = stabilityConfig.getMaxRollAngle();
        BigDecimal criticalRoll = stabilityConfig.getCriticalRollAngle();
        BigDecimal absRoll = rollAngle.abs();

        if (absRoll.compareTo(criticalRoll) > 0) {
            Alarm alarm = createAlarm(shipId, "ROLL_CRITICAL", "CRITICAL",
                    String.format("横摇角严重超标: |%.2f°| > %.2f°，货物移位风险！",
                            rollAngle.doubleValue(), criticalRoll.doubleValue()),
                    stability.getId(), sensorData != null ? sensorData.getId() : null);
            alarms.add(alarm);
        } else if (absRoll.compareTo(maxRoll) > 0) {
            Alarm alarm = createAlarm(shipId, "ROLL_EXCESSIVE", "WARNING",
                    String.format("横摇角超标: |%.2f°| > %.2f°",
                            rollAngle.doubleValue(), maxRoll.doubleValue()),
                    stability.getId(), sensorData != null ? sensorData.getId() : null);
            alarms.add(alarm);
        }
    }

    private void checkBilgeWaterAlarm(UUID shipId, BigDecimal bilgeWater, StabilityResult stability,
                                       SensorData sensorData, List<Alarm> alarms) {
        BigDecimal maxBilge = stabilityConfig.getMaxBilgeWater();
        BigDecimal criticalBilge = stabilityConfig.getCriticalBilgeWater();

        if (bilgeWater.compareTo(criticalBilge) > 0) {
            Alarm alarm = createAlarm(shipId, "BILGE_CRITICAL", "CRITICAL",
                    String.format("舱底水严重超标: %.3fm > %.3fm，可能漏水！",
                            bilgeWater.doubleValue(), criticalBilge.doubleValue()),
                    stability.getId(), sensorData != null ? sensorData.getId() : null);
            alarms.add(alarm);
        } else if (bilgeWater.compareTo(maxBilge) > 0) {
            Alarm alarm = createAlarm(shipId, "BILGE_HIGH", "WARNING",
                    String.format("舱底水位偏高: %.3fm > %.3fm，建议检查排水系统",
                            bilgeWater.doubleValue(), maxBilge.doubleValue()),
                    stability.getId(), sensorData != null ? sensorData.getId() : null);
            alarms.add(alarm);
        }
    }

    private Alarm createAlarm(UUID shipId, String type, String severity, String description,
                               UUID stabilityResultId, UUID sensorDataId) {
        Alarm alarm = new Alarm();
        alarm.setShipId(shipId);
        alarm.setAlarmType(type);
        alarm.setSeverity(severity);
        alarm.setDescription(description);
        alarm.setTriggeredAt(LocalDateTime.now());
        alarm.setAcknowledged(false);
        alarm.setStabilityResultId(stabilityResultId);
        alarm.setSensorDataId(sensorDataId);
        return alarm;
    }

    private boolean shouldTriggerAlarm(UUID shipId, String alarmType, String severity) {
        LocalDateTime cooldownTime = LocalDateTime.now().minusMinutes(ALARM_COOLDOWN_MINUTES);
        List<Alarm> recent = alarmRepository.findRecentAlarms(shipId, alarmType, severity, cooldownTime);
        return recent.isEmpty();
    }

    public AlarmDTO acknowledgeAlarm(UUID alarmId, String acknowledgedBy) {
        Optional<Alarm> alarmOpt = alarmRepository.findById(alarmId);
        if (alarmOpt.isEmpty()) {
            throw new IllegalArgumentException("告警不存在: " + alarmId);
        }
        Alarm alarm = alarmOpt.get();
        alarm.setAcknowledged(true);
        alarm.setAcknowledgedAt(LocalDateTime.now());
        alarm.setAcknowledgedBy(acknowledgedBy);
        Alarm saved = alarmRepository.save(alarm);
        return convertToDTO(saved);
    }

    public List<AlarmDTO> getActiveAlarms(UUID shipId) {
        List<Alarm> alarms = alarmRepository.findByShipIdAndAcknowledgedFalseOrderByTriggeredAtDesc(shipId);
        List<AlarmDTO> dtos = new ArrayList<>();
        for (Alarm alarm : alarms) {
            dtos.add(convertToDTO(alarm));
        }
        return dtos;
    }

    public AlarmDTO convertToDTO(Alarm alarm) {
        AlarmDTO dto = new AlarmDTO();
        dto.setId(alarm.getId());
        dto.setShipId(alarm.getShipId());
        dto.setAlarmType(alarm.getAlarmType());
        dto.setAlarmLevel(alarm.getAlarmLevel());
        dto.setSeverity(alarm.getSeverity());
        dto.setAlarmMessage(alarm.getAlarmMessage());
        dto.setDescription(alarm.getDescription());
        dto.setTriggeredAt(alarm.getTriggeredAt() != null ? alarm.getTriggeredAt() : alarm.getAlarmTime());
        dto.setAlarmTime(alarm.getAlarmTime());
        dto.setIsAcknowledged(alarm.getIsAcknowledged());
        dto.setAcknowledged(alarm.getAcknowledged());
        dto.setAcknowledgedAt(alarm.getAcknowledgedAt());
        dto.setAcknowledgedBy(alarm.getAcknowledgedBy());
        dto.setStabilityResultId(alarm.getStabilityResultId());
        dto.setSensorDataId(alarm.getSensorDataId());
        dto.setParameterName(alarm.getParameterName());
        dto.setParameterValue(alarm.getParameterValue());
        dto.setThresholdValue(alarm.getThresholdValue());
        dto.setCreatedAt(alarm.getCreatedAt());
        return dto;
    }
}
