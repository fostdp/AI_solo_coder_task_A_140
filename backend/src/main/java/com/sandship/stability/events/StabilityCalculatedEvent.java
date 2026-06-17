package com.sandship.stability.events;

import com.sandship.stability.dto.StabilityResultDTO;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public class StabilityCalculatedEvent extends ApplicationEvent {

    private final UUID shipId;
    private final StabilityResultDTO stabilityResult;
    private final LocalDateTime calculatedAt;

    public StabilityCalculatedEvent(Object source, UUID shipId, StabilityResultDTO stabilityResult) {
        super(source);
        this.shipId = shipId;
        this.stabilityResult = stabilityResult;
        this.calculatedAt = LocalDateTime.now();
    }

    public UUID getShipId() {
        return shipId;
    }

    public StabilityResultDTO getStabilityResult() {
        return stabilityResult;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }
}
