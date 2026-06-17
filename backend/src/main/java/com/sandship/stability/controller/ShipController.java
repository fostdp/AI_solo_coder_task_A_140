package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.repository.ShipRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ships")
@Tag(name = "船舶管理", description = "船舶信息管理接口")
public class ShipController {

    @Autowired
    private ShipRepository shipRepository;

    @GetMapping
    @Operation(summary = "获取所有船舶列表")
    public ApiResponse<List<Ship>> getAllShips() {
        return ApiResponse.success(shipRepository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取船舶信息")
    public ApiResponse<Ship> getShipById(@PathVariable UUID id) {
        Optional<Ship> ship = shipRepository.findById(id);
        return ship.map(ApiResponse::success)
                .orElse(ApiResponse.error("船舶不存在"));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索船舶")
    public ApiResponse<List<Ship>> searchShips(@RequestParam String keyword) {
        return ApiResponse.success(shipRepository.searchByName(keyword));
    }

    @PostMapping
    @Operation(summary = "创建船舶")
    public ApiResponse<Ship> createShip(@RequestBody Ship ship) {
        ship.setId(null);
        Ship saved = shipRepository.save(ship);
        return ApiResponse.success("船舶创建成功", saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新船舶信息")
    public ApiResponse<Ship> updateShip(@PathVariable UUID id, @RequestBody Ship ship) {
        if (!shipRepository.existsById(id)) {
            return ApiResponse.error("船舶不存在");
        }
        ship.setId(id);
        Ship saved = shipRepository.save(ship);
        return ApiResponse.success("船舶更新成功", saved);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除船舶")
    public ApiResponse<Void> deleteShip(@PathVariable UUID id) {
        if (!shipRepository.existsById(id)) {
            return ApiResponse.error("船舶不存在");
        }
        shipRepository.deleteById(id);
        return ApiResponse.success("船舶删除成功", null);
    }
}
