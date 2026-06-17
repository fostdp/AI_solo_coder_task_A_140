package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.dto.SensorDataDTO;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.repository.SensorDataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/sensor-data")
@Tag(name = "传感器数据", description = "传感器数据查询接口")
public class SensorDataController {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @GetMapping("/ship/{shipId}")
    @Operation(summary = "分页获取船舶传感器数据")
    public ApiResponse<Page<SensorData>> getSensorDataByShip(
            @PathVariable UUID shipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<SensorData> result = sensorDataRepository.findByShipIdOrderByTimestampDesc(
                shipId, PageRequest.of(page, size, Sort.by("timestamp").descending()));
        return ApiResponse.success(result);
    }

    @GetMapping("/ship/{shipId}/latest")
    @Operation(summary = "获取船舶最新传感器数据")
    public ApiResponse<SensorData> getLatestSensorData(@PathVariable UUID shipId) {
        Optional<SensorData> data = sensorDataRepository.findTopByShipIdOrderByTimestampDesc(shipId);
        return data.map(ApiResponse::success)
                .orElse(ApiResponse.error("暂无传感器数据"));
    }

    @GetMapping("/ship/{shipId}/history")
    @Operation(summary = "获取船舶历史传感器数据")
    public ApiResponse<List<SensorData>> getSensorDataHistory(
            @PathVariable UUID shipId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        List<SensorData> data = sensorDataRepository
                .findByShipIdAndTimestampBetweenOrderByTimestampDesc(shipId, startTime, endTime);
        return ApiResponse.success(data);
    }

    @GetMapping("/ship/{shipId}/recent")
    @Operation(summary = "获取船舶最近N条传感器数据")
    public ApiResponse<List<SensorData>> getRecentSensorData(
            @PathVariable UUID shipId,
            @RequestParam(defaultValue = "100") int limit) {
        List<SensorData> data = sensorDataRepository.findLatestN(shipId, limit);
        return ApiResponse.success(data);
    }

    @PostMapping("/manual")
    @Operation(summary = "手动录入传感器数据")
    public ApiResponse<SensorData> createSensorData(@RequestBody SensorDataDTO dto) {
        SensorData data = new SensorData();
        data.setShipId(dto.getShipId());
        data.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());
        data.setDraftForward(dto.getDraftForward());
        data.setDraftAft(dto.getDraftAft());
        data.setDraftMean(dto.getDraftMean());
        data.setRollAngle(dto.getRollAngle());
        data.setPitchAngle(dto.getPitchAngle());
        data.setHeelAngle(dto.getHeelAngle());
        data.setBilgeWaterLevel(dto.getBilgeWaterLevel());
        data.setWaterTemperature(dto.getWaterTemperature());
        data.setWindSpeed(dto.getWindSpeed());
        data.setWindDirection(dto.getWindDirection());
        data.setWaveHeight(dto.getWaveHeight());
        data.setMqttTopic("MANUAL");
        data.setRawPayload(dto.getRawPayload());

        if (data.getDraftMean() == null && data.getDraftForward() != null
                && data.getDraftAft() != null) {
            data.setDraftMean(data.getDraftForward().add(data.getDraftAft())
                    .divide(java.math.BigDecimal.valueOf(2), 3, java.math.RoundingMode.HALF_UP));
        }

        SensorData saved = sensorDataRepository.save(data);
        return ApiResponse.success("传感器数据录入成功", saved);
    }
}
