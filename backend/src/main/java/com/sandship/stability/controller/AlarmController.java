package com.sandship.stability.controller;

import com.sandship.stability.dto.AlarmDTO;
import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.entity.Alarm;
import com.sandship.stability.repository.AlarmRepository;
import com.sandship.stability.alarm_ws.AlarmEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/alarms")
@Tag(name = "告警管理", description = "告警查询与处理接口")
public class AlarmController {

    @Autowired
    private AlarmEvaluatorService alarmEvaluatorService;

    @Autowired
    private AlarmRepository alarmRepository;

    @GetMapping("/ship/{shipId}/active")
    @Operation(summary = "获取船舶未处理告警")
    public ApiResponse<List<AlarmDTO>> getActiveAlarms(@PathVariable UUID shipId) {
        return ApiResponse.success(alarmEvaluatorService.getActiveAlarms(shipId));
    }

    @GetMapping("/ship/{shipId}/count")
    @Operation(summary = "获取船舶未处理告警数量")
    public ApiResponse<Map<String, Long>> getUnacknowledgedCount(@PathVariable UUID shipId) {
        long count = alarmRepository.countByShipIdAndAcknowledgedFalse(shipId);
        return ApiResponse.success(Map.of("count", count, "shipId", shipId.toString()));
    }

    @GetMapping("/ship/{shipId}")
    @Operation(summary = "分页获取船舶告警历史")
    public ApiResponse<Page<AlarmDTO>> getAlarmHistory(
            @PathVariable UUID shipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlarmDTO> results = alarmRepository.findByShipIdOrderByTriggeredAtDesc(
                        shipId, PageRequest.of(page, size, Sort.by("triggeredAt").descending()))
                .map(alarmEvaluatorService::convertToDTO);
        return ApiResponse.success(results);
    }

    @GetMapping("/ship/{shipId}/level/{level}")
    @Operation(summary = "按级别获取船舶告警")
    public ApiResponse<List<AlarmDTO>> getAlarmsByLevel(
            @PathVariable UUID shipId,
            @PathVariable String level) {
        List<AlarmDTO> results = alarmRepository
                .findByShipIdAndSeverityOrderByTriggeredAtDesc(shipId, level.toUpperCase())
                .stream()
                .map(alarmEvaluatorService::convertToDTO)
                .toList();
        return ApiResponse.success(results);
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "确认单个告警")
    public ApiResponse<AlarmDTO> acknowledgeAlarm(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String userName) {
        try {
            AlarmDTO result = alarmEvaluatorService.acknowledgeAlarm(id, userName);
            return ApiResponse.success("告警已确认", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/ship/{shipId}/acknowledge-all")
    @Operation(summary = "确认船舶所有告警")
    public ApiResponse<Map<String, Object>> acknowledgeAllAlarms(@PathVariable UUID shipId) {
        List<Alarm> alarms = alarmRepository.findByShipIdAndAcknowledgedFalseOrderByTriggeredAtDesc(shipId);
        int count = 0;
        for (Alarm alarm : alarms) {
            alarm.setAcknowledged(true);
            alarm.setAcknowledgedAt(java.time.LocalDateTime.now());
            alarm.setAcknowledgedBy("batch");
            alarmRepository.save(alarm);
            count++;
        }
        return ApiResponse.success(Map.of(
                "message", String.format("已确认 %d 条告警", count),
                "count", count,
                "shipId", shipId.toString()
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取告警详情")
    public ApiResponse<AlarmDTO> getAlarmById(@PathVariable UUID id) {
        return alarmRepository.findById(id)
                .map(alarm -> ApiResponse.success(alarmEvaluatorService.convertToDTO(alarm)))
                .orElse(ApiResponse.error("告警不存在"));
    }
}
