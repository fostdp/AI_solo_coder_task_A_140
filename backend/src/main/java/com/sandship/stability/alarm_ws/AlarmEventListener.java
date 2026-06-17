package com.sandship.stability.alarm_ws;

import com.sandship.stability.entity.SensorData;
import com.sandship.stability.events.LoadingOptimizedEvent;
import com.sandship.stability.events.SensorDataReceivedEvent;
import com.sandship.stability.events.StabilityCalculatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlarmEventListener {

    @Autowired
    private AlarmEvaluatorService alarmEvaluatorService;

    @Autowired
    private AlarmWebSocketNotifier webSocketNotifier;

    @Async
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            log.debug("[Alarm Listener] 收到传感器数据事件，启动告警检测 - 船舶: {}", event.getShipId());
            SensorData sensorData = event.getSensorData();
            alarmEvaluatorService.checkAndTriggerAlarms(event.getShipId(), sensorData);
        } catch (Exception e) {
            log.error("[Alarm Listener] 处理传感器数据告警失败 - 船舶: {}", event.getShipId(), e);
        }
    }

    @Async
    @EventListener
    public void onStabilityCalculated(StabilityCalculatedEvent event) {
        try {
            log.debug("[Alarm Listener] 收到稳性计算事件，广播WebSocket - 船舶: {}", event.getShipId());
            if (event.getStabilityResult() != null) {
                webSocketNotifier.broadcastStabilityUpdate(event.getShipId(), event.getStabilityResult());
            }
        } catch (Exception e) {
            log.error("[Alarm Listener] 广播稳性数据失败 - 船舶: {}", event.getShipId(), e);
        }
    }

    @Async
    @EventListener
    public void onLoadingOptimized(LoadingOptimizedEvent event) {
        try {
            log.debug("[Alarm Listener] 收到装载优化事件，广播WebSocket - 船舶: {}", event.getShipId());
            if (event.getOptimizationResult() != null) {
                webSocketNotifier.broadcastOptimizationUpdate(event.getShipId(), event.getOptimizationResult());
            }
        } catch (Exception e) {
            log.error("[Alarm Listener] 广播优化结果失败 - 船舶: {}", event.getShipId(), e);
        }
    }
}
