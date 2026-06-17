package com.sandship.stability.events;

import com.sandship.stability.dto.LoadingOptimizationResultDTO;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public class LoadingOptimizedEvent extends ApplicationEvent {

    private final UUID shipId;
    private final LoadingOptimizationResultDTO optimizationResult;
    private final LocalDateTime optimizedAt;

    public LoadingOptimizedEvent(Object source, UUID shipId, LoadingOptimizationResultDTO optimizationResult) {
        super(source);
        this.shipId = shipId;
        this.optimizationResult = optimizationResult;
        this.optimizedAt = LocalDateTime.now();
    }

    public UUID getShipId() {
        return shipId;
    }

    public LoadingOptimizationResultDTO getOptimizationResult() {
        return optimizationResult;
    }

    public LocalDateTime getOptimizedAt() {
        return optimizedAt;
    }
}
