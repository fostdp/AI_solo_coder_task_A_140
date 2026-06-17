package com.sandship.stability.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandship.stability.dto.AlarmDTO;
import com.sandship.stability.dto.StabilityResultDTO;
import com.sandship.stability.dto.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class StabilityWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> shipSessions = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接建立 - Session: {}", session.getId());
        allSessions.add(session);

        String shipId = extractShipId(session);
        if (shipId != null) {
            shipSessions.computeIfAbsent(shipId, k -> new CopyOnWriteArraySet<>())
                    .add(session);
            log.info("Session {} 订阅船舶: {}", session.getId(), shipId);
        }

        sendWelcomeMessage(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息 - Session: {}, Payload: {}", session.getId(), payload);

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");

            if ("SUBSCRIBE".equals(type)) {
                String shipId = (String) msg.get("shipId");
                if (shipId != null) {
                    shipSessions.computeIfAbsent(shipId, k -> new CopyOnWriteArraySet<>())
                            .add(session);
                    log.info("Session {} 订阅船舶: {}", session.getId(), shipId);
                }
            } else if ("UNSUBSCRIBE".equals(type)) {
                String shipId = (String) msg.get("shipId");
                if (shipId != null) {
                    Set<WebSocketSession> sessions = shipSessions.get(shipId);
                    if (sessions != null) {
                        sessions.remove(session);
                        log.info("Session {} 取消订阅船舶: {}", session.getId(), shipId);
                    }
                }
            } else if ("PING".equals(type)) {
                sendMessage(session, WebSocketMessage.create("PONG", null,
                        Map.of("timestamp", LocalDateTime.now().toString())));
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket连接关闭 - Session: {}, Status: {}", session.getId(), status);
        allSessions.remove(session);

        for (Map.Entry<String, Set<WebSocketSession>> entry : shipSessions.entrySet()) {
            entry.getValue().remove(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误 - Session: {}", session.getId(), exception);
    }

    public <T> void broadcast(String shipId, WebSocketMessage<T> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            if ("ALL".equals(shipId)) {
                for (WebSocketSession session : allSessions) {
                    sendMessageAsync(session, textMessage);
                }
            } else {
                Set<WebSocketSession> sessions = shipSessions.get(shipId);
                if (sessions != null) {
                    for (WebSocketSession session : sessions) {
                        sendMessageAsync(session, textMessage);
                    }
                }
            }
        } catch (Exception e) {
            log.error("广播消息失败 - 船舶: {}", shipId, e);
        }
    }

    public void broadcastStabilityUpdate(String shipId, StabilityResultDTO stability) {
        WebSocketMessage<StabilityResultDTO> message = WebSocketMessage.create(
                "STABILITY_UPDATE", shipId, stability);
        broadcast(shipId, message);
        broadcast("ALL", message);
    }

    public void broadcastAlarm(String shipId, AlarmDTO alarm) {
        WebSocketMessage<AlarmDTO> message = WebSocketMessage.create(
                "ALARM", shipId, alarm);
        broadcast(shipId, message);
        broadcast("ALL", message);
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
    }

    private void sendMessageAsync(WebSocketSession session, TextMessage message) {
        if (session.isOpen()) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.error("发送消息失败 - Session: {}", session.getId(), e);
            }
        }
    }

    private void sendWelcomeMessage(WebSocketSession session) throws IOException {
        Map<String, Object> welcomeData = Map.of(
                "message", "连接成功",
                "sessionId", session.getId(),
                "timestamp", LocalDateTime.now().toString()
        );
        sendMessage(session, WebSocketMessage.create("WELCOME", null, welcomeData));
    }

    private String extractShipId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "shipId".equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    public int getConnectedCount() {
        return allSessions.size();
    }

    public int getShipSubscriberCount(String shipId) {
        Set<WebSocketSession> sessions = shipSessions.get(shipId);
        return sessions != null ? sessions.size() : 0;
    }
}
