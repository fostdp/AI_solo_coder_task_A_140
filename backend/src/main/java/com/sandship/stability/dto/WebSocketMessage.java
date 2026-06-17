package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {

    private String type;
    private String shipId;
    private T payload;
    private LocalDateTime timestamp;

    public static <T> WebSocketMessage<T> create(String type, String shipId, T payload) {
        return new WebSocketMessage<>(type, shipId, payload, LocalDateTime.now());
    }
}
