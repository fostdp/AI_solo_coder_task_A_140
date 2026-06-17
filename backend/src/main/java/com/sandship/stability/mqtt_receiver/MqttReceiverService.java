package com.sandship.stability.mqtt_receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandship.stability.dto.SensorDataDTO;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.events.SensorDataReceivedEvent;
import com.sandship.stability.repository.SensorDataRepository;
import com.sandship.stability.repository.ShipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
public class MqttReceiverService implements MessageHandler {

    private static final Pattern SHIP_ID_PATTERN = Pattern.compile("ship/([^/]+)/sensor");

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private SensorDataValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            log.debug("[MQTT Receiver] 收到消息 - Topic: {}", topic);
            processMessage(topic, payload);
        } catch (Exception e) {
            log.error("[MQTT Receiver] 处理MQTT消息失败", e);
        }
    }

    @Async
    public void processMessage(String topic, String payload) {
        UUID shipId = extractShipId(topic);
        if (shipId == null) {
            log.warn("[MQTT Receiver] 无法从Topic提取船舶ID: {}", topic);
            return;
        }

        Optional<Ship> shipOpt = shipRepository.findById(shipId);
        if (shipOpt.isEmpty()) {
            log.warn("[MQTT Receiver] 未找到船舶: {}", shipId);
            return;
        }

        try {
            SensorDataDTO sensorDataDTO = parsePayload(payload);
            SensorData sensorData = convertToEntity(sensorDataDTO, shipId, topic, payload);

            if (sensorData.getDraftMean() == null && sensorData.getDraftForward() != null
                    && sensorData.getDraftAft() != null) {
                BigDecimal meanDraft = sensorData.getDraftForward().add(sensorData.getDraftAft())
                        .divide(new BigDecimal("2"), 3, BigDecimal.ROUND_HALF_UP);
                sensorData.setDraftMean(meanDraft);
            }

            SensorDataValidator.ValidationResult validation = validator.validate(shipId, sensorData);
            if (!validation.isValid()) {
                log.warn("[MQTT Receiver] 数据校验失败 - 船舶: {}, 错误: {}", shipId, validation.getErrors());
                return;
            }

            sensorData = sensorDataRepository.save(sensorData);
            log.info("[MQTT Receiver] 传感器数据已保存 - 船舶: {}, 时间: {}", shipId, sensorData.getTimestamp());

            eventPublisher.publishEvent(new SensorDataReceivedEvent(this, shipId, sensorData));

        } catch (Exception e) {
            log.error("[MQTT Receiver] 处理传感器数据失败 - Topic: {}, Payload: {}", topic, payload, e);
        }
    }

    private UUID extractShipId(String topic) {
        try {
            Matcher matcher = SHIP_ID_PATTERN.matcher(topic);
            if (matcher.find()) {
                return UUID.fromString(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("[MQTT Receiver] 解析船舶ID失败: {}", topic);
        }
        return null;
    }

    private SensorDataDTO parsePayload(String payload) throws Exception {
        try {
            return objectMapper.readValue(payload, SensorDataDTO.class);
        } catch (Exception e) {
            SensorDataDTO dto = new SensorDataDTO();
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            if (root.containsKey("hull")) {
                Map<String, Object> hull = (Map<String, Object>) root.get("hull");
                dto.setDraftForward(toBigDecimal(hull.get("draft_forward")));
                dto.setDraftAft(toBigDecimal(hull.get("draft_aft")));
                dto.setDraftMean(toBigDecimal(hull.get("draft_depth")));
                dto.setRollAngle(toBigDecimal(hull.get("roll_angle")));
                dto.setPitchAngle(toBigDecimal(hull.get("pitch_angle")));
            }
            if (root.containsKey("bilge")) {
                Map<String, Object> bilge = (Map<String, Object>) root.get("bilge");
                dto.setBilgeWaterLevel(toBigDecimal(bilge.get("water_level")));
            }
            if (root.containsKey("weather")) {
                Map<String, Object> weather = (Map<String, Object>) root.get("weather");
                dto.setWindSpeed(toBigDecimal(weather.get("wind_speed")));
                dto.setWindDirection(toBigDecimal(weather.get("wind_direction")));
                dto.setWaveHeight(toBigDecimal(weather.get("wave_height")));
            }
            return dto;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
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
