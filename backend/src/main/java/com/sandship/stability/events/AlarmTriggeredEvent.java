package com.sandship.stability.events;

import com.sandship.stability.dto.AlarmDTO;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public class AlarmTriggeredEvent extends ApplicationEvent {

    private final UUID shipId;
    private final AlarmDTO alarm;
    private final LocalDateTime triggeredAt;

    public AlarmTriggeredEvent(Object source, UUID shipId, AlarmDTO alarm) {
        super(source);
        this.shipId = shipId;
        this.alarm = alarm;
        this.triggeredAt = LocalDateTime.now();
    }

    public UUID getShipId() {
        return shipId;
    }

    public AlarmDTO getAlarm() {
        return alarm;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }
}
