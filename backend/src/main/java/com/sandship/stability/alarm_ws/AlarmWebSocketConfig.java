package com.sandship.stability.alarm_ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AlarmWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AlarmWebSocketNotifier alarmWebSocketNotifier;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alarmWebSocketNotifier, "/ws/alarm", "/ws/stability")
                .setAllowedOrigins("*");
    }
}
