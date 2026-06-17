package com.sandship.stability.controller;

import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.websocket.StabilityWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/system")
@Tag(name = "系统管理", description = "系统状态与配置接口")
public class SystemController {

    @Autowired
    private StabilityWebSocketHandler webSocketHandler;

    @Autowired
    private StabilityConfig stabilityConfig;

    @GetMapping("/status")
    @Operation(summary = "获取系统状态")
    public ApiResponse<Map<String, Object>> getSystemStatus() {
        return ApiResponse.success(Map.of(
                "status", "ONLINE",
                "timestamp", LocalDateTime.now().toString(),
                "websocketConnections", webSocketHandler.getConnectedCount(),
                "minGmThreshold", stabilityConfig.getMinGmThreshold(),
                "maxRollAngle", stabilityConfig.getMaxRollAngle(),
                "maxBilgeWater", stabilityConfig.getMaxBilgeWater()
        ));
    }

    @GetMapping("/websocket/stats")
    @Operation(summary = "获取WebSocket连接统计")
    public ApiResponse<Map<String, Object>> getWebSocketStats() {
        return ApiResponse.success(Map.of(
                "totalConnections", webSocketHandler.getConnectedCount(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/config/stability")
    @Operation(summary = "获取稳性阈值配置")
    public ApiResponse<StabilityConfig> getStabilityConfig() {
        return ApiResponse.success(stabilityConfig);
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public ApiResponse<Map<String, Object>> healthCheck() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "service", "sand-ship-stability-system",
                "version", "1.0.0"
        ));
    }
}
