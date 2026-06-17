package com.sandship.stability.controller;

import com.sandship.stability.dto.AlarmDTO;
import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.repository.AlarmRepository;
import com.sandship.stability.service.AlarmService;
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
    private AlarmService alarmService;

    @Autowired
    private AlarmRepository alarmRepository;

    @GetMapping("/ship/{shipId}/active")
    @Operation(summary = "获取船舶未处理告警")
    public ApiResponse<List<AlarmDTO>> getActiveAlarms(@PathVariable UUID shipId) {
        return ApiResponse.success(alarmService.getActiveAlarms(shipId));
    }

    @GetMapping("/ship/{shipId}/count")
    @Operation(summary = "获取船舶未处理告警数量")
    public ApiResponse<Map<String, Long>> getUnacknowledgedCount(@PathVariable UUID shipId) {
        long count = alarmService.countUnacknowledgedAlarms(shipId);
        return ApiResponse.success(Map.of("count", count, "shipId", shipId.toString()));
    }

    @GetMapping("/ship/{shipId}")
    @Operation(summary = "分页获取船舶告警历史")
    public ApiResponse<Page<AlarmDTO>> getAlarmHistory(
            @PathVariable UUID shipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlarmDTO> results = alarmRepository.findByShipIdOrderByAlarmTimeDesc(
                        shipId, PageRequest.of(page, size, Sort.by("alarmTime").descending()))
                .map(alarmService::convertToDTO);
        return ApiResponse.success(results);
    }

    @GetMapping("/ship/{shipId}/level/{level}")
    @Operation(summary = "按级别获取船舶告警")
    public ApiResponse<List<AlarmDTO>> getAlarmsByLevel(
            @PathVariable UUID shipId,
            @PathVariable String level) {
        List<AlarmDTO> results = alarmRepository
                .findByShipIdAndAlarmLevelOrderByAlarmTimeDesc(shipId, level.toUpperCase())
                .stream()
                .map(alarmService::convertToDTO)
                .toList();
        return ApiResponse.success(results);
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "确认单个告警")
    public ApiResponse<AlarmDTO> acknowledgeAlarm(@PathVariable UUID id) {
        Optional<AlarmDTO> result = alarmService.acknowledgeAlarm(id);
        return result.map(alarm -> ApiResponse.success("告警已确认", alarm))
                .orElse(ApiResponse.error("告警不存在"));
    }

    @PostMapping("/ship/{shipId}/acknowledge-all")
    @Operation(summary = "确认船舶所有告警")
    public ApiResponse<Map<String, Object>> acknowledgeAllAlarms(@PathVariable UUID shipId) {
        int count = alarmService.acknowledgeAllAlarms(shipId);
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
                .map(alarm -> ApiResponse.success(alarmService.convertToDTO(alarm)))
                .orElse(ApiResponse.error("告警不存在"));
    }
}
