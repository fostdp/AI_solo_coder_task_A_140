package com.sandship.stability.stability_simulator;

import com.sandship.stability.entity.SensorData;
import com.sandship.stability.events.SensorDataReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StabilityEventListener {

    @Autowired
    private StabilitySimulatorService stabilitySimulatorService;

    @Async
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            log.debug("[Stability Listener] 收到传感器数据事件 - 船舶: {}", event.getShipId());
            SensorData sensorData = event.getSensorData();
            stabilitySimulatorService.calculateAndSaveStability(event.getShipId(), sensorData);
        } catch (Exception e) {
            log.error("[Stability Listener] 处理传感器数据事件失败 - 船舶: {}", event.getShipId(), e);
        }
    }
}
