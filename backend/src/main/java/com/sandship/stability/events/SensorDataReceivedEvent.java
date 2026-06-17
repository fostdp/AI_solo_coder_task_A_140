package com.sandship.stability.events;

import com.sandship.stability.entity.SensorData;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public class SensorDataReceivedEvent extends ApplicationEvent {

    private final UUID shipId;
    private final SensorData sensorData;
    private final LocalDateTime receivedAt;

    public SensorDataReceivedEvent(Object source, UUID shipId, SensorData sensorData) {
        super(source);
        this.shipId = shipId;
        this.sensorData = sensorData;
        this.receivedAt = LocalDateTime.now();
    }

    public UUID getShipId() {
        return shipId;
    }

    public SensorData getSensorData() {
        return sensorData;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
