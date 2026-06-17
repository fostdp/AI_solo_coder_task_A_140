package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.dto.LoadingOptimizationRequest;
import com.sandship.stability.dto.LoadingOptimizationResultDTO;
import com.sandship.stability.entity.LoadingOptimization;
import com.sandship.stability.repository.LoadingOptimizationRepository;
import com.sandship.stability.service.LoadingOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/loading-optimization")
@Tag(name = "装载优化", description = "装载优化算法接口")
public class LoadingOptimizationController {

    @Autowired
    private LoadingOptimizationService loadingOptimizationService;

    @Autowired
    private LoadingOptimizationRepository optimizationRepository;

    @PostMapping("/optimize")
    @Operation(summary = "执行装载优化")
    public ApiResponse<LoadingOptimizationResultDTO> optimizeLoading(
            @RequestBody LoadingOptimizationRequest request) {
        try {
            LoadingOptimizationResultDTO result = loadingOptimizationService
                    .optimizeLoading(request);
            return ApiResponse.success("装载优化完成", result);
        } catch (Exception e) {
            return ApiResponse.error("装载优化失败: " + e.getMessage());
        }
    }

    @GetMapping("/ship/{shipId}/latest")
    @Operation(summary = "获取船舶最新装载优化结果")
    public ApiResponse<LoadingOptimizationResultDTO> getLatestOptimization(
            @PathVariable UUID shipId) {
        Optional<LoadingOptimization> opt = loadingOptimizationService
                .getLatestOptimization(shipId);
        if (opt.isEmpty()) {
            return ApiResponse.error("暂无装载优化记录");
        }

        LoadingOptimization optimization = opt.get();
        LoadingOptimizationResultDTO dto = new LoadingOptimizationResultDTO();
        dto.setId(optimization.getId());
        dto.setShipId(optimization.getShipId());
        dto.setOptimizationTime(optimization.getOptimizationTime());
        dto.setTotalCargoWeight(optimization.getTotalCargoWeight());
        dto.setTotalCargoVolume(optimization.getTotalCargoVolume());
        dto.setEffectivePayload(optimization.getEffectivePayload());
        dto.setMinGmRequired(optimization.getMinGmRequired());
        dto.setResultingGm(optimization.getResultingGm());
        dto.setGrainWeight(optimization.getGrainWeight());
        dto.setSaltWeight(optimization.getSaltWeight());
        dto.setStatus(optimization.getStatus());
        dto.setSolution(optimization.getSolution());
        dto.setObjectiveValue(optimization.getObjectiveValue());
        dto.setCreatedAt(optimization.getCreatedAt());

        return ApiResponse.success(dto);
    }

    @GetMapping("/ship/{shipId}")
    @Operation(summary = "分页获取船舶装载优化历史")
    public ApiResponse<Page<LoadingOptimization>> getOptimizationHistory(
            @PathVariable UUID shipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LoadingOptimization> results = optimizationRepository
                .findByShipIdOrderByOptimizationTimeDesc(
                        shipId, PageRequest.of(page, size, Sort.by("optimizationTime").descending()));
        return ApiResponse.success(results);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取装载优化详情")
    public ApiResponse<LoadingOptimization> getOptimizationById(@PathVariable UUID id) {
        Optional<LoadingOptimization> opt = optimizationRepository.findById(id);
        return opt.map(ApiResponse::success)
                .orElse(ApiResponse.error("装载优化记录不存在"));
    }
}
