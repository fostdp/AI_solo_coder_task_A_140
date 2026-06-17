package com.sandship.stability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandship.stability.dto.SensorDataDTO;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.repository.SensorDataRepository;
import com.sandship.stability.repository.ShipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MqttMessageHandler implements MessageHandler {

    private static final Pattern SHIP_ID_PATTERN = Pattern.compile("ship/([^/]+)/sensor");

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private StabilityCalculationService stabilityCalculationService;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();

            log.debug("收到MQTT消息 - Topic: {}, Payload: {}", topic, payload);

            processMessage(topic, payload);

        } catch (Exception e) {
            log.error("处理MQTT消息失败", e);
        }
    }

    @Async
    public void processMessage(String topic, String payload) {
        try {
            UUID shipId = extractShipId(topic);
            if (shipId == null) {
                log.warn("无法从Topic提取船舶ID: {}", topic);
                return;
            }

            Optional<Ship> shipOpt = shipRepository.findById(shipId);
            if (shipOpt.isEmpty()) {
                log.warn("未找到船舶: {}", shipId);
                return;
            }

            SensorDataDTO sensorDataDTO = objectMapper.readValue(payload, SensorDataDTO.class);
            SensorData sensorData = convertToEntity(sensorDataDTO, shipId, topic, payload);

            if (sensorData.getDraftMean() == null && sensorData.getDraftForward() != null
                    && sensorData.getDraftAft() != null) {
                BigDecimal meanDraft = sensorData.getDraftForward().add(sensorData.getDraftAft())
                        .divide(new BigDecimal("2"), 3, BigDecimal.ROUND_HALF_UP);
                sensorData.setDraftMean(meanDraft);
            }

            sensorData = sensorDataRepository.save(sensorData);
            log.debug("传感器数据已保存 - 船舶: {}, 时间: {}", shipId, sensorData.getTimestamp());

            stabilityCalculationService.calculateAndSaveStability(shipId, sensorData);

            alarmService.checkAndTriggerAlarms(shipId, sensorData);

        } catch (Exception e) {
            log.error("处理传感器数据失败 - Topic: {}, Payload: {}", topic, payload, e);
        }
    }

    private UUID extractShipId(String topic) {
        try {
            Matcher matcher = SHIP_ID_PATTERN.matcher(topic);
            if (matcher.find()) {
                String idStr = matcher.group(1);
                return UUID.fromString(idStr);
            }
        } catch (Exception e) {
            log.warn("解析船舶ID失败: {}", topic);
        }
        return null;
    }

    private SensorData convertToEntity(SensorDataDTO dto, UUID shipId, String topic, String rawPayload) {
        SensorData entity = new SensorData();
        entity.setShipId(shipId);
        entity.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());
        entity.setDraftForward(dto.getDraftForward());
        entity.setDraftAft(dto.getDraftAft());
        entity.setDraftMean(dto.getDraftMean());
        entity.setRollAngle(dto.getRollAngle());
        entity.setPitchAngle(dto.getPitchAngle());
        entity.setHeelAngle(dto.getHeelAngle());
        entity.setBilgeWaterLevel(dto.getBilgeWaterLevel());
        entity.setWaterTemperature(dto.getWaterTemperature());
        entity.setWindSpeed(dto.getWindSpeed());
        entity.setWindDirection(dto.getWindDirection());
        entity.setWaveHeight(dto.getWaveHeight());
        entity.setMqttTopic(topic);

        try {
            Map<String, Object> payloadMap = objectMapper.readValue(rawPayload, Map.class);
            entity.setRawPayload(payloadMap);
        } catch (Exception e) {
            Map<String, Object> rawMap = new HashMap<>();
            rawMap.put("raw", rawPayload);
            entity.setRawPayload(rawMap);
        }

        return entity;
    }
}
