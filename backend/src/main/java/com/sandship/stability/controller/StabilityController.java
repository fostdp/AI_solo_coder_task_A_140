package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.dto.StabilityResultDTO;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.StabilityResult;
import com.sandship.stability.repository.SensorDataRepository;
import com.sandship.stability.repository.StabilityResultRepository;
import com.sandship.stability.service.StabilityCalculationService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stability")
@Tag(name = "稳性计算", description = "稳性仿真与计算接口")
public class StabilityController {

    @Autowired
    private StabilityCalculationService stabilityCalculationService;

    @Autowired
    private StabilityResultRepository stabilityResultRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @GetMapping("/ship/{shipId}/latest")
    @Operation(summary = "获取船舶最新稳性计算结果")
    public ApiResponse<StabilityResultDTO> getLatestStability(@PathVariable UUID shipId) {
        Optional<StabilityResult> result = stabilityCalculationService.getLatestStability(shipId);
        return result.map(r -> ApiResponse.success(stabilityCalculationService.convertToDTO(r)))
                .orElse(ApiResponse.error("暂无稳性数据"));
    }

    @GetMapping("/ship/{shipId}")
    @Operation(summary = "分页获取船舶稳性历史数据")
    public ApiResponse<Page<StabilityResultDTO>> getStabilityHistory(
            @PathVariable UUID shipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<StabilityResult> results = stabilityResultRepository
                .findByShipIdOrderByCalculationTimeDesc(
                        shipId, PageRequest.of(page, size, Sort.by("calculationTime").descending()));
        Page<StabilityResultDTO> dtoPage = results.map(stabilityCalculationService::convertToDTO);
        return ApiResponse.success(dtoPage);
    }

    @GetMapping("/ship/{shipId}/range")
    @Operation(summary = "获取指定时间范围内的稳性数据")
    public ApiResponse<List<StabilityResultDTO>> getStabilityByTimeRange(
            @PathVariable UUID shipId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        List<StabilityResult> results = stabilityCalculationService
                .getStabilityHistory(shipId, startTime, endTime);
        List<StabilityResultDTO> dtos = results.stream()
                .map(stabilityCalculationService::convertToDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @GetMapping("/ship/{shipId}/warnings")
    @Operation(summary = "获取船舶稳性警告记录")
    public ApiResponse<List<StabilityResultDTO>> getStabilityWarnings(@PathVariable UUID shipId) {
        List<StabilityResult> results = stabilityResultRepository.findWarningsByShipId(shipId);
        List<StabilityResultDTO> dtos = results.stream()
                .map(stabilityCalculationService::convertToDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @PostMapping("/calculate/{shipId}")
    @Operation(summary = "手动触发稳性计算")
    public ApiResponse<StabilityResultDTO> calculateStability(@PathVariable UUID shipId) {
        try {
            Optional<SensorData> latestSensorData = sensorDataRepository
                    .findTopByShipIdOrderByTimestampDesc(shipId);
            StabilityResult result = stabilityCalculationService
                    .calculateAndSaveStability(shipId, latestSensorData.orElse(null));
            return ApiResponse.success("稳性计算完成",
                    stabilityCalculationService.convertToDTO(result));
        } catch (Exception e) {
            return ApiResponse.error("稳性计算失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取稳性计算详情")
    public ApiResponse<StabilityResultDTO> getStabilityById(@PathVariable UUID id) {
        Optional<StabilityResult> result = stabilityResultRepository.findById(id);
        return result.map(r -> ApiResponse.success(stabilityCalculationService.convertToDTO(r)))
                .orElse(ApiResponse.error("稳性记录不存在"));
    }
}
