package com.sandship.stability.loading_optimizer;

import com.sandship.stability.dto.LoadingOptimizationRequest;
import com.sandship.stability.dto.LoadingOptimizationResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LoadingOptimizerEventListener {

    @Autowired
    private LoadingOptimizerService loadingOptimizerService;

    @Async
    @EventListener
    public void onStabilityCalculated(com.sandship.stability.events.StabilityCalculatedEvent event) {
        try {
            if (event.getStabilityResult() != null
                    && "NORMAL".equals(event.getStabilityResult().getStabilityStatus())) {
                log.debug("[Loading Optimizer Listener] 稳性正常，可进行装载优化 - 船舶: {}", event.getShipId());
            }
        } catch (Exception e) {
            log.warn("[Loading Optimizer Listener] 处理稳性计算事件异常: {}", e.getMessage());
        }
    }
}
