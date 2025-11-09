package com.example.webui.websocket;

import com.example.webui.repository.UserView;
import com.example.webui.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserStateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UserStateWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public UserStateWebSocketHandler(ObjectMapper objectMapper, UserService userService) {
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.debug("WebSocket session established: {}", session.getId());
        sendSnapshot(List.of(session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.debug("WebSocket session closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // No-op: server pushes updates only
    }

    public void broadcastSnapshot() {
        sendSnapshot(sessions);
    }

    private void sendSnapshot(Iterable<WebSocketSession> targetSessions) {
        if (!targetSessions.iterator().hasNext()) {
            return;
        }

        List<UserView> users = userService.findAll();
        UserStateMessage payload = new UserStateMessage(
            Instant.now(),
            users.stream()
                .map(user -> new UserState(
                    user.id().toString(),
                    user.code(),
                    user.currentZoneCode() != null ? user.currentZoneCode() : "OUT",
                    user.updatedAt()
                ))
                .toList()
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user state payload", e);
            return;
        }

        for (WebSocketSession session : targetSessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.warn("Failed to send user state to session {}", session.getId(), e);
            }
        }
    }

    public record UserStateMessage(
        Instant generatedAt,
        List<UserState> users
    ) {}

    public record UserState(
        String id,
        String code,
        String zoneCode,
        Instant updatedAt
    ) {}
}


