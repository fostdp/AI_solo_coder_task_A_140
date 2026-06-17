package com.sandship.stability.service;

import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.AlarmDTO;
import com.sandship.stability.dto.WebSocketMessage;
import com.sandship.stability.entity.Alarm;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.entity.StabilityResult;
import com.sandship.stability.repository.AlarmRepository;
import com.sandship.stability.repository.ShipRepository;
import com.sandship.stability.repository.StabilityResultRepository;
import com.sandship.stability.websocket.StabilityWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AlarmService {

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private StabilityResultRepository stabilityResultRepository;

    @Autowired
    private StabilityConfig stabilityConfig;

    @Autowired
    private StabilityWebSocketHandler webSocketHandler;

    private static final long ALARM_COOLDOWN_SECONDS = 300;

    @Transactional
    public List<Alarm> checkAndTriggerAlarms(UUID shipId, SensorData sensorData) {
        List<Alarm> triggeredAlarms = new ArrayList<>();

        Optional<StabilityResult> stabilityOpt = stabilityResultRepository
                .findTopByShipIdOrderByCalculationTimeDesc(shipId);

        if (stabilityOpt.isPresent()) {
            StabilityResult stability = stabilityOpt.get();

            Alarm gmAlarm = checkGmAlarm(shipId, sensorData, stability);
            if (gmAlarm != null) triggeredAlarms.add(gmAlarm);

            Alarm rollAlarm = checkRollAngleAlarm(shipId, sensorData, stability);
            if (rollAlarm != null) triggeredAlarms.add(rollAlarm);
        }

        Alarm bilgeAlarm = checkBilgeWaterAlarm(shipId, sensorData);
        if (bilgeAlarm != null) triggeredAlarms.add(bilgeAlarm);

        for (Alarm alarm : triggeredAlarms) {
            if (shouldTriggerAlarm(shipId, alarm.getAlarmType())) {
                alarm = alarmRepository.save(alarm);
                broadcastAlarm(alarm);
                log.warn("告警触发 - 船舶: {}, 类型: {}, 级别: {}, 消息: {}",
                        shipId, alarm.getAlarmType(), alarm.getAlarmLevel(),
                        alarm.getAlarmMessage());
            }
        }

        return triggeredAlarms;
    }

    private Alarm checkGmAlarm(UUID shipId, SensorData sensorData,
                                StabilityResult stability) {
        BigDecimal gm = stability.getGmValue();
        BigDecimal minGm = stabilityConfig.getMinGmThreshold();

        if (gm == null || gm.compareTo(minGm) >= 0) {
            return null;
        }

        String level = gm.compareTo(new BigDecimal("0.15")) < 0 ? "CRITICAL" : "WARNING";
        String message = String.format("GM值过低: 当前%.3fm, 阈值%.3fm",
                gm.doubleValue(), minGm.doubleValue());

        return createAlarm(shipId, sensorData, stability, "GM_TOO_LOW", level,
                message, "GM值", gm, minGm);
    }

    private Alarm checkRollAngleAlarm(UUID shipId, SensorData sensorData,
                                       StabilityResult stability) {
        if (sensorData == null || sensorData.getRollAngle() == null) {
            return null;
        }

        BigDecimal rollAngle = sensorData.getRollAngle().abs();
        BigDecimal maxRoll = stabilityConfig.getMaxRollAngle();

        if (rollAngle.compareTo(maxRoll) <= 0) {
            return null;
        }

        String level = rollAngle.compareTo(new BigDecimal("25")) > 0 ? "CRITICAL" : "WARNING";
        String message = String.format("横摇角过大: 当前%.2f°, 阈值%.2f°",
                rollAngle.doubleValue(), maxRoll.doubleValue());

        return createAlarm(shipId, sensorData, stability, "ROLL_EXCEEDED", level,
                message, "横摇角", rollAngle, maxRoll);
    }

    private Alarm checkBilgeWaterAlarm(UUID shipId, SensorData sensorData) {
        if (sensorData == null || sensorData.getBilgeWaterLevel() == null) {
            return null;
        }

        BigDecimal bilgeWater = sensorData.getBilgeWaterLevel();
        BigDecimal maxBilge = stabilityConfig.getMaxBilgeWater();

        if (bilgeWater.compareTo(maxBilge) <= 0) {
            return null;
        }

        String level = bilgeWater.compareTo(new BigDecimal("1.0")) > 0 ? "CRITICAL" : "WARNING";
        String message = String.format("舱底水位过高: 当前%.3fm, 阈值%.3fm",
                bilgeWater.doubleValue(), maxBilge.doubleValue());

        return createAlarm(shipId, sensorData, null, "BILGE_WATER_HIGH", level,
                message, "舱底水位", bilgeWater, maxBilge);
    }

    private Alarm createAlarm(UUID shipId, SensorData sensorData,
                              StabilityResult stability, String alarmType,
                              String alarmLevel, String message, String paramName,
                              BigDecimal paramValue, BigDecimal threshold) {
        Alarm alarm = new Alarm();
        alarm.setShipId(shipId);
        alarm.setSensorDataId(sensorData != null ? sensorData.getId() : null);
        alarm.setStabilityResultId(stability != null ? stability.getId() : null);
        alarm.setAlarmTime(LocalDateTime.now());
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setAlarmMessage(message);
        alarm.setParameterName(paramName);
        alarm.setParameterValue(paramValue);
        alarm.setThresholdValue(threshold);
        alarm.setIsAcknowledged(false);
        return alarm;
    }

    private boolean shouldTriggerAlarm(UUID shipId, String alarmType) {
        LocalDateTime cooldownTime = LocalDateTime.now()
                .minusSeconds(ALARM_COOLDOWN_SECONDS);

        List<Alarm> recentAlarms = alarmRepository.findRecentAlarms(shipId, cooldownTime);

        return recentAlarms.stream()
                .noneMatch(a -> a.getAlarmType().equals(alarmType));
    }

    private void broadcastAlarm(Alarm alarm) {
        AlarmDTO dto = convertToDTO(alarm);
        WebSocketMessage<AlarmDTO> message = WebSocketMessage.create(
                "ALARM", alarm.getShipId().toString(), dto);
        webSocketHandler.broadcast(alarm.getShipId().toString(), message);
        webSocketHandler.broadcast("ALL", message);
    }

    @Transactional(readOnly = true)
    public List<AlarmDTO> getActiveAlarms(UUID shipId) {
        List<Alarm> alarms = alarmRepository
                .findByShipIdAndIsAcknowledgedFalseOrderByAlarmTimeDesc(shipId);
        return alarms.stream().map(this::convertToDTO).toList();
    }

    @Transactional
    public int acknowledgeAllAlarms(UUID shipId) {
        return alarmRepository.acknowledgeAllByShipId(shipId, LocalDateTime.now());
    }

    @Transactional
    public Optional<AlarmDTO> acknowledgeAlarm(UUID alarmId) {
        Optional<Alarm> alarmOpt = alarmRepository.findById(alarmId);
        if (alarmOpt.isPresent()) {
            Alarm alarm = alarmOpt.get();
            alarm.setIsAcknowledged(true);
            alarm.setAcknowledgedAt(LocalDateTime.now());
            alarmRepository.save(alarm);
            return Optional.of(convertToDTO(alarm));
        }
        return Optional.empty();
    }

    public long countUnacknowledgedAlarms(UUID shipId) {
        return alarmRepository.countUnacknowledgedByShipId(shipId);
    }

    public AlarmDTO convertToDTO(Alarm alarm) {
        AlarmDTO dto = new AlarmDTO();
        dto.setId(alarm.getId());
        dto.setShipId(alarm.getShipId());
        dto.setSensorDataId(alarm.getSensorDataId());
        dto.setStabilityResultId(alarm.getStabilityResultId());
        dto.setAlarmTime(alarm.getAlarmTime());
        dto.setAlarmType(alarm.getAlarmType());
        dto.setAlarmLevel(alarm.getAlarmLevel());
        dto.setAlarmMessage(alarm.getAlarmMessage());
        dto.setParameterName(alarm.getParameterName());
        dto.setParameterValue(alarm.getParameterValue());
        dto.setThresholdValue(alarm.getThresholdValue());
        dto.setIsAcknowledged(alarm.getIsAcknowledged());
        dto.setAcknowledgedAt(alarm.getAcknowledgedAt());
        dto.setCreatedAt(alarm.getCreatedAt());

        shipRepository.findById(alarm.getShipId()).ifPresent(ship ->
                dto.setShipName(ship.getName()));

        return dto;
    }
}
