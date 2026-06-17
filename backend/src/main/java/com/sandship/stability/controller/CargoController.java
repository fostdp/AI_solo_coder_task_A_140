package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.dto.CargoLoadingDTO;
import com.sandship.stability.entity.CargoHold;
import com.sandship.stability.entity.CargoLoading;
import com.sandship.stability.entity.CargoType;
import com.sandship.stability.repository.CargoHoldRepository;
import com.sandship.stability.repository.CargoLoadingRepository;
import com.sandship.stability.repository.CargoTypeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cargo")
@Tag(name = "货物管理", description = "货物类型、货舱、装载管理接口")
public class CargoController {

    @Autowired
    private CargoTypeRepository cargoTypeRepository;

    @Autowired
    private CargoHoldRepository cargoHoldRepository;

    @Autowired
    private CargoLoadingRepository cargoLoadingRepository;

    @GetMapping("/types")
    @Operation(summary = "获取所有货物类型")
    public ApiResponse<List<CargoType>> getAllCargoTypes() {
        return ApiResponse.success(cargoTypeRepository.findAll());
    }

    @GetMapping("/types/{code}")
    @Operation(summary = "根据编码获取货物类型")
    public ApiResponse<CargoType> getCargoTypeByCode(@PathVariable String code) {
        return cargoTypeRepository.findByCargoCode(code)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("货物类型不存在"));
    }

    @GetMapping("/holds/ship/{shipId}")
    @Operation(summary = "获取船舶货舱列表")
    public ApiResponse<List<CargoHold>> getCargoHoldsByShip(@PathVariable UUID shipId) {
        return ApiResponse.success(cargoHoldRepository.findByShipIdOrderByHoldNumber(shipId));
    }

    @GetMapping("/loading/ship/{shipId}")
    @Operation(summary = "获取船舶装载记录")
    public ApiResponse<List<CargoLoadingDTO>> getCargoLoadingsByShip(@PathVariable UUID shipId) {
        List<CargoLoading> loadings = cargoLoadingRepository.findByShipIdWithDetails(shipId);
        List<CargoLoadingDTO> dtos = loadings.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @GetMapping("/loading/ship/{shipId}/summary")
    @Operation(summary = "获取船舶装载汇总")
    public ApiResponse<Map<String, Object>> getLoadingSummary(@PathVariable UUID shipId) {
        BigDecimal totalWeight = cargoLoadingRepository.calculateTotalWeightByShipId(shipId);
        BigDecimal totalVolume = cargoLoadingRepository.calculateTotalVolumeByShipId(shipId);
        List<CargoLoading> loadings = cargoLoadingRepository.findByShipIdWithDetails(shipId);

        Map<String, BigDecimal> weightByType = loadings.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCargoType().getCargoCode(),
                        Collectors.reducing(BigDecimal.ZERO, CargoLoading::getWeight, BigDecimal::add)
                ));

        Map<String, BigDecimal> weightByHold = loadings.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCargoHold().getHoldNumber().toString(),
                        Collectors.reducing(BigDecimal.ZERO, CargoLoading::getWeight, BigDecimal::add)
                ));

        return ApiResponse.success(Map.of(
                "shipId", shipId.toString(),
                "totalWeight", totalWeight,
                "totalVolume", totalVolume,
                "weightByType", weightByType,
                "weightByHold", weightByHold,
                "loadingCount", loadings.size()
        ));
    }

    @PostMapping("/loading")
    @Operation(summary = "添加货物装载记录")
    public ApiResponse<CargoLoadingDTO> addCargoLoading(@RequestBody CargoLoadingDTO dto) {
        CargoLoading loading = new CargoLoading();
        loading.setShipId(dto.getShipId());
        loading.setHoldId(dto.getHoldId());
        loading.setCargoTypeId(dto.getCargoTypeId());
        loading.setWeight(dto.getWeight());
        loading.setVolume(dto.getVolume() != null ? dto.getVolume()
                : dto.getWeight().divide(dto.getDensity() != null ? dto.getDensity()
                : BigDecimal.ONE, 2, java.math.RoundingMode.HALF_UP));
        loading.setLoadingTime(dto.getLoadingTime() != null ? dto.getLoadingTime() : LocalDateTime.now());
        loading.setLoadingOrder(dto.getLoadingOrder());
        loading.setIsOptimized(false);

        CargoLoading saved = cargoLoadingRepository.save(loading);
        return ApiResponse.success("装载记录添加成功", convertToDTO(saved));
    }

    @DeleteMapping("/loading/{id}")
    @Operation(summary = "删除装载记录")
    public ApiResponse<Void> deleteCargoLoading(@PathVariable UUID id) {
        if (!cargoLoadingRepository.existsById(id)) {
            return ApiResponse.error("装载记录不存在");
        }
        cargoLoadingRepository.deleteById(id);
        return ApiResponse.success("装载记录已删除", null);
    }

    @PostMapping("/loading/ship/{shipId}/clear")
    @Operation(summary = "清空船舶所有非优化装载记录")
    public ApiResponse<Map<String, Object>> clearNonOptimizedLoadings(@PathVariable UUID shipId) {
        List<CargoLoading> loadings = cargoLoadingRepository
                .findByShipIdAndOptimized(shipId, false);
        int count = loadings.size();
        cargoLoadingRepository.deleteAll(loadings);
        return ApiResponse.success(Map.of(
                "message", String.format("已删除 %d 条非优化装载记录", count),
                "count", count
        ));
    }

    private CargoLoadingDTO convertToDTO(CargoLoading loading) {
        CargoLoadingDTO dto = new CargoLoadingDTO();
        dto.setId(loading.getId());
        dto.setShipId(loading.getShipId());
        dto.setHoldId(loading.getHoldId());
        dto.setCargoTypeId(loading.getCargoTypeId());
        dto.setWeight(loading.getWeight());
        dto.setVolume(loading.getVolume());
        dto.setLoadingTime(loading.getLoadingTime());
        dto.setLoadingOrder(loading.getLoadingOrder());
        dto.setIsOptimized(loading.getIsOptimized());
        dto.setCreatedAt(loading.getCreatedAt());

        if (loading.getCargoHold() != null) {
            dto.setHoldNumber(loading.getCargoHold().getHoldNumber());
            dto.setHoldName(loading.getCargoHold().getHoldName());
        }

        if (loading.getCargoType() != null) {
            dto.setCargoName(loading.getCargoType().getCargoName());
            dto.setCargoCode(loading.getCargoType().getCargoCode());
            dto.setColorHex(loading.getCargoType().getColorHex());
            dto.setDensity(loading.getCargoType().getDensity());
        }

        if (loading.getShip() != null) {
            dto.setShipName(loading.getShip().getName());
        }

        return dto;
    }
}
