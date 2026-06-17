package com.sandship.stability.alarm_ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandship.stability.dto.AlarmDTO;
import com.sandship.stability.dto.StabilityResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class AlarmWebSocketNotifier extends TextWebSocketHandler {

    private static final String ATTR_SHIP_ID = "shipId";

    private final Map<UUID, Set<WebSocketSession>> shipSessions = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        UUID shipId = extractShipId(query);

        allSessions.add(session);

        if (shipId != null) {
            session.getAttributes().put(ATTR_SHIP_ID, shipId);
            shipSessions.computeIfAbsent(shipId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("[Alarm WS] WebSocket连接建立 - 船舶: {}, Session: {}", shipId, session.getId());
        } else {
            log.info("[Alarm WS] WebSocket连接建立 - 全量订阅, Session: {}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        allSessions.remove(session);

        UUID shipId = (UUID) session.getAttributes().get(ATTR_SHIP_ID);
        if (shipId != null) {
            Set<WebSocketSession> sessions = shipSessions.get(shipId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    shipSessions.remove(shipId);
                }
            }
        }
        log.info("[Alarm WS] WebSocket连接关闭 - Session: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("[Alarm WS] 收到消息 - Session: {}, Content: {}", session.getId(), message.getPayload());
    }

    public void broadcastAlarm(UUID shipId, AlarmDTO alarm) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ALARM");
        payload.put("shipId", shipId);
        payload.put("data", alarm);
        payload.put("timestamp", System.currentTimeMillis());
        sendToShip(shipId, payload);
    }

    public void broadcastStabilityUpdate(UUID shipId, StabilityResultDTO stability) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "STABILITY_UPDATE");
        payload.put("shipId", shipId);
        payload.put("data", stability);
        payload.put("timestamp", System.currentTimeMillis());
        sendToShip(shipId, payload);
    }

    public void broadcastOptimizationUpdate(UUID shipId, Object optimizationResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "OPTIMIZATION_UPDATE");
        payload.put("shipId", shipId);
        payload.put("data", optimizationResult);
        payload.put("timestamp", System.currentTimeMillis());
        sendToShip(shipId, payload);
    }

    public void sendToShip(UUID shipId, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            Set<WebSocketSession> sessions = shipSessions.get(shipId);
            if (sessions != null) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                }
            }

            for (WebSocketSession session : allSessions) {
                if (session.isOpen() && session.getAttributes().get(ATTR_SHIP_ID) == null) {
                    session.sendMessage(textMessage);
                }
            }
        } catch (IOException e) {
            log.error("[Alarm WS] 发送消息失败 - 船舶: {}", shipId, e);
        }
    }

    private UUID extractShipId(String query) {
        if (query == null || query.isEmpty()) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "shipId".equals(kv[0])) {
                try {
                    return UUID.fromString(kv[1]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public Set<WebSocketSession> getAllSessions() {
        return Collections.unmodifiableSet(allSessions);
    }

    public int getSessionCount() {
        return allSessions.size();
    }

    public int getShipSessionCount(UUID shipId) {
        Set<WebSocketSession> sessions = shipSessions.get(shipId);
        return sessions != null ? sessions.size() : 0;
    }
}
